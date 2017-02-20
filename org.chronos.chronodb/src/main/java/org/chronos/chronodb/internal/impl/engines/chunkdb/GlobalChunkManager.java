package org.chronos.chronodb.internal.impl.engines.chunkdb;

import static com.google.common.base.Preconditions.*;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.chronos.chronodb.api.ChronoDBConstants;
import org.chronos.chronodb.internal.api.ChronoDBConfiguration;
import org.chronos.chronodb.internal.impl.engines.tupl.DefaultTuplTransaction;
import org.chronos.chronodb.internal.impl.engines.tupl.TuplUtils;
import org.chronos.chronodb.internal.impl.tupl.TuplTransaction;
import org.chronos.chronodb.internal.impl.tupl.WrappedTuplTransaction;
import org.cojen.tupl.Database;
import org.cojen.tupl.Transaction;
import org.mapdb.DB;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

public class GlobalChunkManager {

	// =================================================================================================================
	// CONSTANTS
	// =================================================================================================================

	private static final int MAX_OPEN_FILES_THRESHOLD = 5;

	// =================================================================================================================
	// FIELDS
	// =================================================================================================================

	private final File branchesDir;
	private final ChronoDBConfiguration config;
	private final Map<String, BranchChunkManager> branchNameToChunkManager;
	private final ReadWriteLock fileSystemLock = new ReentrantReadWriteLock(true);

	private final BiMap<File, Database> fileToOpenDB = HashBiMap.create();
	private final Multimap<Database, InternalTransaction> dbToOpenTransactions = HashMultimap.create();
	private final List<Database> dbLRUList = Lists.newLinkedList();
	private final Lock dbLock = new ReentrantLock();

	public GlobalChunkManager(final File branchesDir, final ChronoDBConfiguration config) {
		checkNotNull(branchesDir, "Precondition violation - argument 'branchesDir' must not be NULL!");
		checkArgument(branchesDir.exists(), "Precondition violation - argument 'branchesDir' must exist!");
		checkArgument(branchesDir.isDirectory(),
				"Precondition violation - argument 'branchesDir' must be a directory!");
		checkNotNull(config, "Precondition violation - argument 'config' must not be NULL!");
		this.branchesDir = branchesDir;
		this.config = config;
		// initialize all branch chunk managers
		this.branchNameToChunkManager = this.scanDirectoryForBranches();
		this.ensureMasterBranchDirectoryExists();
	}

	// =================================================================================================================
	// INITIALIZATION HELPERS
	// =================================================================================================================

	private Map<String, BranchChunkManager> scanDirectoryForBranches() {
		Map<String, BranchChunkManager> branchNameToChunkManager = Maps.newHashMap();
		File[] branchDirs = this.branchesDir.listFiles(file -> file.isDirectory());
		for (File branchDir : branchDirs) {
			BranchChunkManager branchChunkManager = new BranchChunkManager(branchDir);
			branchNameToChunkManager.put(branchDir.getName(), branchChunkManager);
		}
		return branchNameToChunkManager;
	}

	private void ensureMasterBranchDirectoryExists() {
		this.getOrCreateChunkManagerForBranch(ChronoDBConstants.MASTER_BRANCH_IDENTIFIER);
	}

	// =================================================================================================================
	// PUBLIC API
	// =================================================================================================================

	public boolean hasChunkManagerForBranch(final String branchName) {
		checkNotNull(branchName, "Precondition violation - argument 'branchName' must not be NULL!");
		this.fileSystemLock.readLock().lock();
		try {
			return this.getChunkManagerForBranch(branchName) != null;
		} finally {
			this.fileSystemLock.readLock().unlock();
		}
	}

	public BranchChunkManager getChunkManagerForBranch(final String branchName) {
		checkNotNull(branchName, "Precondition violation - argument 'branchName' must not be NULL!");
		this.fileSystemLock.readLock().lock();
		try {
			return this.branchNameToChunkManager.get(branchName);
		} finally {
			this.fileSystemLock.readLock().unlock();
		}
	}

	public BranchChunkManager getOrCreateChunkManagerForBranch(final String branchName) {
		checkNotNull(branchName, "Precondition violation - argument 'branchName' must not be NULL!");
		this.fileSystemLock.writeLock().lock();
		try {
			BranchChunkManager existingManager = this.getChunkManagerForBranch(branchName);
			if (existingManager != null) {
				// manager for the branch already exists
				return existingManager;
			}
			// manager for branch does not exist; create it
			File masterBranchDir = new File(this.branchesDir, branchName);
			if (masterBranchDir.mkdir() == false) {
				throw new IllegalStateException(
						"Failed to create directory '" + masterBranchDir.getAbsolutePath() + "'!");
			}
			BranchChunkManager newBranchManager = new BranchChunkManager(masterBranchDir);
			this.branchNameToChunkManager.put(branchName, newBranchManager);
			return newBranchManager;
		} finally {
			this.fileSystemLock.writeLock().unlock();
		}
	}

	public ChunkTuplTransaction openTransactionOn(final String branch, final long timestamp) {
		checkNotNull(branch, "Precondition violation - argument 'branch' must not be NULL!");
		checkArgument(timestamp >= 0, "Precondition violation - argument 'timestamp' must not be negative!");
		this.dbLock.lock();
		try {
			// get the correct chunk
			ChronoChunk chunk = this.getChunkManagerForBranch(branch).getChunkForTimestamp(timestamp);
			TuplTransaction innerTransaction = this.openTransactionOn(chunk.getDataFile());
			return new ChunkTuplTransaction(innerTransaction, chunk.getMetaData().getValidPeriod());
		} finally {
			this.dbLock.unlock();
		}
	}

	public TuplTransaction openTransactionOn(final File chunkFile) {
		checkNotNull(chunkFile, "Precondition violation - argument 'chunkFile' must not be NULL!");
		return this.openTransactionOn(chunkFile, true);
	}

	public TuplTransaction openBogusTransactionOn(final File chunkFile) {
		checkNotNull(chunkFile, "Precondition violation - argument 'chunkFile' must not be NULL!");
		return this.openTransactionOn(chunkFile, false);
	}

	public void dropChunkIndexFiles() {
		for (BranchChunkManager manager : this.branchNameToChunkManager.values()) {
			manager.dropChunkIndexFiles();
		}
	}

	/**
	 * Ensures that there is no open {@link DB MapDB} instance on the given file.
	 *
	 * <p>
	 * This method assumes that the caller has asserted that there are no open transactions left on the MapDB instance.
	 * If there still are open transactions, an {@link IllegalStateException} will be thrown.
	 *
	 * @param dbFile
	 *            The file to assert for that the corresponding MapDB instance is closed.
	 *
	 * @throws IllegalStateException
	 *             Thrown if there are still open transactions on the MapDB that accesses the given file.
	 */
	public void ensureTuplDbIsClosed(final File dbFile) {
		checkNotNull(dbFile, "Precondition violation - argument 'newChunkDataFile' must not be NULL!");
		this.dbLock.lock();
		try {
			Database db = this.fileToOpenDB.get(dbFile);
			if (db == null) {
				// there was no open DB that used the given file; we're done
				return;
			}
			// check that there are no open transactions on it anymore
			if (this.dbToOpenTransactions.get(db).isEmpty() == false) {
				throw new IllegalStateException(
						"There are still open transactions accessing file '" + dbFile.getAbsolutePath() + "'!");
			}
			this.dbToOpenTransactions.removeAll(db);
			this.fileToOpenDB.remove(dbFile);
			TuplUtils.shutdownQuietly(db);
		} finally {
			this.dbLock.unlock();
		}
	}

	public void shutdown() {
		this.dbLock.lock();
		try {
			for (Entry<File, Database> entry : this.fileToOpenDB.entrySet()) {
				Database openDB = entry.getValue();
				TuplUtils.shutdownQuietly(openDB);
			}
			this.fileToOpenDB.clear();
		} finally {
			this.dbLock.unlock();
		}
	}

	// =====================================================================================================================
	// INTERNAL HELPER METHODS
	// =====================================================================================================================

	private void handleTransactionClosed(final InternalTransaction tx) {
		this.dbLock.lock();
		try {
			Database db = tx.getDB();
			this.dbToOpenTransactions.remove(db, tx);
			if (this.dbToOpenTransactions.containsKey(db) == false) {
				// nobody accesses this DB anymore, check if we can/ need to close it
				this.closeUnusedDB();
			}
		} finally {
			this.dbLock.unlock();
		}
	}

	private void closeUnusedDB() {
		this.dbLock.lock();
		try {
			if (this.fileToOpenDB.size() <= MAX_OPEN_FILES_THRESHOLD) {
				// threshold not exceeded, no need to close any DB
				return;
			}
			List<Database> dbs = Lists.reverse(this.dbLRUList);
			Iterator<Database> dbIterator = dbs.iterator();
			while (dbIterator.hasNext() && dbs.size() > MAX_OPEN_FILES_THRESHOLD) {
				Database db = dbIterator.next();
				if (this.dbToOpenTransactions.containsKey(db) == false) {
					// nobody uses this DB anymore, remove it
					TuplUtils.shutdownQuietly(db);
					this.fileToOpenDB.inverse().remove(db);
					dbIterator.remove();
				}
			}
		} finally {
			this.dbLock.unlock();
		}
	}

	private void touch(final Database db) {
		this.dbLRUList.remove(db);
		this.dbLRUList.add(0, db);
	}

	private TuplTransaction openTransactionOn(final File chunkFile, final boolean realTransaction) {
		checkNotNull(chunkFile, "Precondition violation - argument 'chunkFile' must not be NULL!");
		this.dbLock.lock();
		try {
			// check if a MapDB instance is open for this file
			Database db = this.fileToOpenDB.get(chunkFile);
			if (db == null) {
				// no database is open yet, open a MapDB instance for this chunk file
				db = TuplUtils.openDatabase(chunkFile, this.config);
				// register the new DB instance
				this.fileToOpenDB.put(chunkFile, db);
			}
			TuplTransaction tx = null;
			if (realTransaction) {
				tx = new DefaultTuplTransaction(db, db.newTransaction());
			} else {
				tx = new DefaultTuplTransaction(db, Transaction.BOGUS);
			}
			InternalTransaction internalTx = new InternalTransaction(tx);
			// register the transaction before returning it
			this.dbToOpenTransactions.put(db, internalTx);
			// move the db to the front in LRU
			this.touch(db);
			return internalTx;
		} finally {
			this.dbLock.unlock();
		}
	}

	// =================================================================================================================
	// INNER CLASSES
	// =================================================================================================================

	/**
	 * A simple extension of {@link TuplTransaction} with a callback that fires when it is closed.
	 *
	 * @author martin.haeusler@uibk.ac.at -- Initial Contribution and API
	 */
	private class InternalTransaction extends WrappedTuplTransaction {

		private boolean closed = false;

		public InternalTransaction(final TuplTransaction innerTx) {
			super(innerTx);
		}

		@Override
		public void close() {
			super.close();
			if (this.closed == false) {
				GlobalChunkManager.this.handleTransactionClosed(this);
				this.closed = true;
			}
		}

		@Override
		public void commit() {
			super.commit();
			if (this.closed == false) {
				GlobalChunkManager.this.handleTransactionClosed(this);
				this.closed = true;
			}
		}

		@Override
		public void rollback() {
			super.rollback();
			if (this.closed == false) {
				GlobalChunkManager.this.handleTransactionClosed(this);
				this.closed = true;
			}
		}

	}

}
