package org.chronos.chronograph.api.structure;

import java.io.File;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.GraphFactoryClass;
import org.chronos.chronodb.api.ChronoDB;
import org.chronos.chronodb.api.ChronoDBConstants;
import org.chronos.chronodb.api.DumpOption;
import org.chronos.chronodb.api.Order;
import org.chronos.chronograph.api.ChronoGraphFactory;
import org.chronos.chronograph.api.branch.ChronoGraphBranchManager;
import org.chronos.chronograph.api.builder.query.GraphQueryBuilderStarter;
import org.chronos.chronograph.api.index.ChronoGraphIndexManager;
import org.chronos.chronograph.api.transaction.ChronoGraphTransactionManager;
import org.chronos.chronograph.internal.api.configuration.ChronoGraphConfiguration;
import org.chronos.chronograph.internal.impl.factory.ChronoGraphFactoryImpl;

@Graph.OptIn(Graph.OptIn.SUITE_STRUCTURE_STANDARD)
@Graph.OptOut(test = "org.apache.tinkerpop.gremlin.structure.io.IoGraphTest", method = "shouldReadWriteModernToFileWithHelpers", specific = "graphml", reason = "The Gremlin Test Suite has File I/O issues on Windows.")
@Graph.OptOut(test = "org.apache.tinkerpop.gremlin.structure.io.IoGraphTest", method = "shouldReadWriteClassicToFileWithHelpers", specific = "graphml", reason = "The Gremlin Test Suite has File I/O issues on Windows.")
@Graph.OptOut(test = "org.apache.tinkerpop.gremlin.structure.io.IoGraphTest", method = "shouldReadWriteModernToFileWithHelpers", specific = "graphson", reason = "The Gremlin Test Suite has File I/O issues on Windows.")
@Graph.OptOut(test = "org.apache.tinkerpop.gremlin.structure.io.IoGraphTest", method = "shouldReadWriteClassicToFileWithHelpers", specific = "graphson", reason = "The Gremlin Test Suite has File I/O issues on Windows.")
@Graph.OptOut(test = "org.apache.tinkerpop.gremlin.structure.io.IoGraphTest", method = "shouldReadWriteModernToFileWithHelpers", specific = "gryo", reason = "The Gremlin Test Suite has File I/O issues on Windows.")
@Graph.OptOut(test = "org.apache.tinkerpop.gremlin.structure.io.IoGraphTest", method = "shouldReadWriteClassicToFileWithHelpers", specific = "gryo", reason = "The Gremlin Test Suite has File I/O issues on Windows.")
@Graph.OptOut(test = "org.apache.tinkerpop.gremlin.structure.io.IoTest$GraphMLTest", method = "shouldProperlyEncodeWithGraphML", reason = "The Gremlin Test Suite has File I/O issues on Windows.")
@Graph.OptOut(test = "org.apache.tinkerpop.gremlin.structure.GraphConstructionTest", method = "shouldMaintainOriginalConfigurationObjectGivenToFactory", reason = "ChronoGraph internally adds configuration properties, test checks only property count.")
@GraphFactoryClass(ChronoGraphFactoryImpl.class)
public interface ChronoGraph extends Graph {

	public static final ChronoGraphFactory FACTORY = ChronoGraphFactory.INSTANCE;

	// =================================================================================================================
	// CONFIGURATION
	// =================================================================================================================

	/**
	 * Returns the {@link ChronoGraphConfiguration} associated with this graph instance.
	 *
	 * <p>
	 * This is similar to {@link #configuration()}, except that this method returns an object that provides handy
	 * accessor methods for the underlying data.
	 *
	 * @return The graph configuration.
	 */
	public ChronoGraphConfiguration getChronoGraphConfiguration();

	// =====================================================================================================================
	// GRAPH CLOSING
	// =====================================================================================================================

	/**
	 * Closes this graph instance.
	 *
	 * <p>
	 * After closing a graph, no further data can be retrieved from or stored in it. Calling this method on an already
	 * closed graph has no effect.
	 */
	@Override
	public void close(); // note: redefined from Graph without 'throws Exception' declaration.

	/**
	 * Checks if this graph instance is closed or not.
	 *
	 * <p>
	 * If this method returns <code>true</code>, data access methods on the graph will throw exceptions when attempting
	 * to execute them.
	 *
	 * @return <code>true</code> if closed, <code>false</code> if it is still open.
	 */
	public boolean isClosed();

	// =====================================================================================================================
	// TRANSACTION HANDLING
	// =====================================================================================================================

	/**
	 * Returns the "now" timestamp, i.e. the timestamp of the latest commit on the graph, on the master branch.
	 *
	 * <p>
	 * Requesting a transaction on this timestamp will always deliver a transaction on the "head" revision.
	 *
	 * @return The "now" timestamp. Will be zero if no commit has been taken place yet, otherwise a positive value.
	 */
	public long getNow();

	/**
	 * Returns the "now" timestamp, i.e. the timestamp of the latest commit on the graph, on the given branch.
	 *
	 * <p>
	 * Requesting a transaction on this timestamp will always deliver a transaction on the "head" revision.
	 *
	 * @param branchName
	 *            The name of the branch to retrieve the "now" timestamp for. Must refer to an existing branch. Must not
	 *            be <code>null</code>.
	 *
	 * @return The "now" timestamp on the given branch. If no commits have occurred on the branch yet, this method
	 *         returns zero (master branch) or the branching timestamp (non-master branch), otherwise a positive value.
	 */
	public long getNow(final String branchName);

	/**
	 * Returns the {@linkplain ChronoGraphTransactionManager transaction manager} associated with this graph instance.
	 *
	 * @return The transaction manager. Never <code>null</code>.
	 */
	@Override
	public ChronoGraphTransactionManager tx();

	// =====================================================================================================================
	// TEMPORAL ACTIONS
	// =====================================================================================================================

	/**
	 * Returns the history of the vertex with the given id, in the form of timestamps.
	 *
	 * <p>
	 * Each returned timestamp reflects a point in time when a commit occurred that changed the vertex in question. The
	 * same commit may have simultaneously also changed other elements in the graph. Opening a transaction on this
	 * timestamp, and retrieving the vertex by id from it will produce the vertex in the state at that point in time.
	 *
	 * <p>
	 * <b>NOTE:</b> This method requires an open transaction! The returned history will always be the history up to and
	 * including (but not after) the transaction timestamp!
	 *
	 * @param vertexId
	 *            The id of the vertex to fetch the history timestamps for. Must not be <code>null</code>.
	 * @return An iterator over the history timestamps. May be empty, but never <code>null</code>. The exact ordering of
	 *         the returned keys is up to the implementation. Implementations should try to return the timestamps in
	 *         descending order (highest first).
	 */
	public Iterator<Long> getVertexHistory(Object vertexId);

	/**
	 * Returns the history of the given vertex, in the form of timestamps.
	 *
	 * <p>
	 * Each returned timestamp reflects a point in time when a commit occurred that changed the vertex in question. The
	 * same commit may have simultaneously also changed other elements in the graph. Opening a transaction on this
	 * timestamp, and retrieving the vertex by id from it will produce the vertex in the state at that point in time.
	 *
	 * <p>
	 * <b>NOTE:</b> This method requires an open transaction! The returned history will always be the history up to and
	 * including (but not after) the transaction timestamp!
	 *
	 * @param vertex
	 *            The vertex to fetch the history timestamps for. Must not be <code>null</code>.
	 * @return An iterator over the history timestamps. May be empty, but never <code>null</code>. The exact ordering of
	 *         the returned keys is up to the implementation. Implementations should try to return the timestamps in
	 *         descending order (highest first).
	 */
	public Iterator<Long> getVertexHistory(Vertex vertex);

	/**
	 * Returns the history of the edge with the given id, in the form of timestamps.
	 *
	 * <p>
	 * Each returned timestamp reflects a point in time when a commit occurred that changed the edge in question. The
	 * same commit may have simultaneously also changed other elements in the graph. Opening a transaction on this
	 * timestamp, and retrieving the edge by id from it will produce the vertex in the state at that point in time.
	 *
	 * <p>
	 * <b>NOTE:</b> This method requires an open transaction! The returned history will always be the history up to and
	 * including (but not after) the transaction timestamp!
	 *
	 * @param edgeId
	 *            The id of the edge to fetch the history timestamps for. Must not be <code>null</code>.
	 * @return An iterator over the history timestamps. May be empty, but never <code>null</code>. The exact ordering of
	 *         the returned keys is up to the implementation. Implementations should try to return the timestamps in
	 *         descending order (highest first).
	 */
	public Iterator<Long> getEdgeHistory(Object edgeId);

	/**
	 * Returns the history of the given edge, in the form of timestamps.
	 *
	 * <p>
	 * Each returned timestamp reflects a point in time when a commit occurred that changed the edge in question. The
	 * same commit may have simultaneously also changed other elements in the graph. Opening a transaction on this
	 * timestamp, and retrieving the edge by id from it will produce the edge in the state at that point in time.
	 *
	 * <p>
	 * <b>NOTE:</b> This method requires an open transaction! The returned history will always be the history up to and
	 * including (but not after) the transaction timestamp!
	 *
	 * @param edge
	 *            The edge to fetch the history timestamps for. Must not be <code>null</code>.
	 * @return An iterator over the history timestamps. May be empty, but never <code>null</code>. The exact ordering of
	 *         the returned keys is up to the implementation. Implementations should try to return the timestamps in
	 *         descending order (highest first).
	 */
	public Iterator<Long> getEdgeHistory(Edge edge);

	/**
	 * Returns an iterator over all vertex modifications that have taken place in the given time range.
	 *
	 * @param timestampLowerBound
	 *            The lower bound of the time range to search in. Must not be negative. Must be less than or equal to
	 *            <code>timestampUpperBound</code>. Must be less than or equal to the transaction timestamp.
	 * @param timestampUpperBound
	 *            The upper bound of the time range to search in. Must not be negative. Must be greater than or equal to
	 *            <code>timestampLowerBound</code>. Must be less than or equal to the transaction timestamp.
	 *
	 * @return An iterator over pairs, containing the change timestamp at the first and the modified vertex id at the
	 *         second position. May be empty, but never <code>null</code>.
	 */
	public Iterator<Pair<Long, String>> getVertexModificationsBetween(final long timestampLowerBound,
			final long timestampUpperBound);

	/**
	 * Returns an iterator over all edge modifications that have taken place in the given time range.
	 *
	 * @param timestampLowerBound
	 *            The lower bound of the time range to search in. Must not be negative. Must be less than or equal to
	 *            <code>timestampUpperBound</code>. Must be less than or equal to the transaction timestamp.
	 * @param timestampUpperBound
	 *            The upper bound of the time range to search in. Must not be negative. Must be greater than or equal to
	 *            <code>timestampLowerBound</code>. Must be less than or equal to the transaction timestamp.
	 *
	 * @return An iterator over pairs, containing the change timestamp at the first and the modified edge id at the
	 *         second position. May be empty, but never <code>null</code>.
	 */
	public Iterator<Pair<Long, String>> getEdgeModificationsBetween(final long timestampLowerBound,
			final long timestampUpperBound);

	/**
	 * Returns the metadata for the commit on the {@linkplain ChronoDBConstants#MASTER_BRANCH_IDENTIFIER master} branch
	 * at the given timestamp.
	 *
	 * <p>
	 * This search will include origin branches (recursively), if the timestamp is before the branching timestamp.
	 *
	 * @param timestamp
	 *            The timestamp to get the commit metadata for. Must match the commit timestamp exactly. Must not be
	 *            negative.
	 *
	 * @return The commit metadata. May be <code>null</code> if there was no metadata for the commit, or there has not
	 *         been a commit at the specified branch and timestamp.
	 */
	public default Object getCommitMetadata(final long timestamp) {
		return this.getCommitMetadata(ChronoDBConstants.MASTER_BRANCH_IDENTIFIER, timestamp);
	}

	/**
	 * Returns the metadata for the commit on the given branch at the given timestamp.
	 *
	 * <p>
	 * This search will include origin branches (recursively), if the timestamp is before the branching timestamp.
	 *
	 * @param branch
	 *            The branch to search for the commit metadata in. Must not be <code>null</code>.
	 * @param timestamp
	 *            The timestamp to get the commit metadata for. Must match the commit timestamp exactly. Must not be
	 *            negative.
	 *
	 * @return The commit metadata. May be <code>null</code> if there was no metadata for the commit, or there has not
	 *         been a commit at the specified branch and timestamp.
	 */
	public Object getCommitMetadata(String branch, long timestamp);

	/**
	 * Returns an iterator over all timestamps where commits have occurred on the
	 * {@linkplain ChronoDBConstants#MASTER_BRANCH_IDENTIFIER master} branch, bounded between <code>from</code> and
	 * <code>to</code>, in descending order.
	 *
	 * <p>
	 * If the <code>from</code> value is greater than the <code>to</code> value, this method always returns an empty
	 * iterator.
	 *
	 * @param from
	 *            The lower bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param to
	 *            The upper bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 *
	 * @return The iterator over the commit timestamps in the given time range, in descending order. May be empty, but
	 *         never <code>null</code>.
	 */
	public default Iterator<Long> getCommitTimestampsBetween(final long from, final long to) {
		return this.getCommitTimestampsBetween(ChronoDBConstants.MASTER_BRANCH_IDENTIFIER, from, to);
	}

	/**
	 * Returns an iterator over all timestamps where commits have occurred on the given branch, bounded between
	 * <code>from</code> and <code>to</code>, in descending order.
	 *
	 * <p>
	 * If the <code>from</code> value is greater than the <code>to</code> value, this method always returns an empty
	 * iterator.
	 *
	 * @param branch
	 *            The name of the branch to consider. Must not be <code>null</code>, must refer to an existing branch.
	 * @param from
	 *            The lower bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param to
	 *            The upper bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 *
	 * @return The iterator over the commit timestamps in the given time range, in descending order. May be empty, but
	 *         never <code>null</code>.
	 */
	public default Iterator<Long> getCommitTimestampsBetween(final String branch, final long from, final long to) {
		return this.getCommitTimestampsBetween(branch, from, to, Order.DESCENDING);
	}

	/**
	 * Returns an iterator over all timestamps where commits have occurred on the
	 * {@linkplain ChronoDBConstants#MASTER_BRANCH_IDENTIFIER master} branch, bounded between <code>from</code> and
	 * <code>to</code>.
	 *
	 * <p>
	 * If the <code>from</code> value is greater than the <code>to</code> value, this method always returns an empty
	 * iterator.
	 *
	 * @param from
	 *            The lower bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param to
	 *            The upper bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param order
	 *            The order of the returned timestamps. Must not be <code>null</code>.
	 *
	 * @return The iterator over the commit timestamps in the given time range. May be empty, but never
	 *         <code>null</code>.
	 */
	public default Iterator<Long> getCommitTimestampsBewteen(final long from, final long to, final Order order) {
		return this.getCommitTimestampsBetween(ChronoDBConstants.MASTER_BRANCH_IDENTIFIER, from, to, order);
	}

	/**
	 * Returns an iterator over all timestamps where commits have occurred on the
	 * {@linkplain ChronoDBConstants#MASTER_BRANCH_IDENTIFIER master} branch, bounded between <code>from</code> and
	 * <code>to</code>.
	 *
	 * <p>
	 * If the <code>from</code> value is greater than the <code>to</code> value, this method always returns an empty
	 * iterator.
	 *
	 * @param from
	 *            The lower bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param to
	 *            The upper bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param order
	 *            The order of the returned timestamps. Must not be <code>null</code>.
	 *
	 * @return The iterator over the commit timestamps in the given time range. May be empty, but never
	 *         <code>null</code>.
	 */
	public default Iterator<Long> getCommitTimestampsBetween(final long from, final long to, final Order order) {
		return this.getCommitTimestampsBetween(ChronoDBConstants.MASTER_BRANCH_IDENTIFIER, from, to, order);
	}

	/**
	 * Returns an iterator over all timestamps where commits have occurred, bounded between <code>from</code> and
	 * <code>to</code>.
	 *
	 * <p>
	 * If the <code>from</code> value is greater than the <code>to</code> value, this method always returns an empty
	 * iterator.
	 *
	 * @param branch
	 *            The name of the branch to consider. Must not be <code>null</code>, must refer to an existing branch.
	 * @param from
	 *            The lower bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param to
	 *            The upper bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param order
	 *            The order of the returned timestamps. Must not be <code>null</code>.
	 *
	 * @return The iterator over the commit timestamps in the given time range. May be empty, but never
	 *         <code>null</code>.
	 */
	public Iterator<Long> getCommitTimestampsBetween(final String branch, long from, long to, Order order);

	/**
	 * Returns an iterator over the entries of commit timestamp and associated metadata on the
	 * {@linkplain ChronoDBConstants#MASTER_BRANCH_IDENTIFIER master} branch, bounded between <code>from</code> and
	 * <code>to</code>, in descending order.
	 *
	 * <p>
	 * If the <code>from</code> value is greater than the <code>to</code> value, this method always returns an empty
	 * iterator.
	 *
	 * <p>
	 * Please keep in mind that some commits may not have any metadata attached. In this case, the
	 * {@linkplain Entry#getValue() value} component of the {@link Entry} will be set to <code>null</code>.
	 *
	 * @param from
	 *            The lower bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param to
	 *            The upper bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 *
	 * @return An iterator over the commits in the given time range in descending order. The contained entries have the
	 *         timestamp as the {@linkplain Entry#getKey() key} component and the associated metadata as their
	 *         {@linkplain Entry#getValue() value} component (which may be <code>null</code>). May be empty, but never
	 *         <code>null</code>.
	 */
	public default Iterator<Entry<Long, Object>> getCommitMetadataBetween(final long from, final long to) {
		return this.getCommitMetadataBetween(ChronoDBConstants.MASTER_BRANCH_IDENTIFIER, from, to, Order.DESCENDING);
	}

	/**
	 * Returns an iterator over the entries of commit timestamp and associated metadata, bounded between
	 * <code>from</code> and <code>to</code>, in descending order.
	 *
	 * <p>
	 * If the <code>from</code> value is greater than the <code>to</code> value, this method always returns an empty
	 * iterator.
	 *
	 * <p>
	 * Please keep in mind that some commits may not have any metadata attached. In this case, the
	 * {@linkplain Entry#getValue() value} component of the {@link Entry} will be set to <code>null</code>.
	 *
	 * @param branch
	 *            The name of the branch to consider. Must not be <code>null</code>, must refer to an existing branch.
	 * @param from
	 *            The lower bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param to
	 *            The upper bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 *
	 * @return An iterator over the commits in the given time range in descending order. The contained entries have the
	 *         timestamp as the {@linkplain Entry#getKey() key} component and the associated metadata as their
	 *         {@linkplain Entry#getValue() value} component (which may be <code>null</code>). May be empty, but never
	 *         <code>null</code>.
	 */
	public default Iterator<Entry<Long, Object>> getCommitMetadataBetween(final String branch, final long from,
			final long to) {
		return this.getCommitMetadataBetween(branch, from, to, Order.DESCENDING);
	}

	/**
	 * Returns an iterator over the entries of commit timestamp and associated metadata on the
	 * {@linkplain ChronoDBConstants#MASTER_BRANCH_IDENTIFIER master} branch, bounded between <code>from</code> and
	 * <code>to</code>.
	 *
	 * <p>
	 * If the <code>from</code> value is greater than the <code>to</code> value, this method always returns an empty
	 * iterator.
	 *
	 * <p>
	 * Please keep in mind that some commits may not have any metadata attached. In this case, the
	 * {@linkplain Entry#getValue() value} component of the {@link Entry} will be set to <code>null</code>.
	 *
	 * @param from
	 *            The lower bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param to
	 *            The upper bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param order
	 *            The order of the returned commits. Must not be <code>null</code>.
	 *
	 * @return An iterator over the commits in the given time range. The contained entries have the timestamp as the
	 *         {@linkplain Entry#getKey() key} component and the associated metadata as their
	 *         {@linkplain Entry#getValue() value} component (which may be <code>null</code>). May be empty, but never
	 *         <code>null</code>.
	 */
	public default Iterator<Entry<Long, Object>> getCommitMetadataBetween(final long from, final long to,
			final Order order) {
		return this.getCommitMetadataBetween(ChronoDBConstants.MASTER_BRANCH_IDENTIFIER, from, to, order);
	}

	/**
	 * Returns an iterator over the entries of commit timestamp and associated metadata, bounded between
	 * <code>from</code> and <code>to</code>.
	 *
	 * <p>
	 * If the <code>from</code> value is greater than the <code>to</code> value, this method always returns an empty
	 * iterator.
	 *
	 * <p>
	 * Please keep in mind that some commits may not have any metadata attached. In this case, the
	 * {@linkplain Entry#getValue() value} component of the {@link Entry} will be set to <code>null</code>.
	 *
	 * @param branch
	 *            The name of the branch to consider. Must not be <code>null</code>, must refer to an existing branch.
	 * @param from
	 *            The lower bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param to
	 *            The upper bound of the time range to look for commits in (inclusive). Must not be negative. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param order
	 *            The order of the returned commits. Must not be <code>null</code>.
	 *
	 * @return An iterator over the commits in the given time range. The contained entries have the timestamp as the
	 *         {@linkplain Entry#getKey() key} component and the associated metadata as their
	 *         {@linkplain Entry#getValue() value} component (which may be <code>null</code>). May be empty, but never
	 *         <code>null</code>.
	 */
	public Iterator<Entry<Long, Object>> getCommitMetadataBetween(String branch, long from, long to, Order order);

	/**
	 * Returns an iterator over commit timestamps on the {@linkplain ChronoDBConstants#MASTER_BRANCH_IDENTIFIER master}
	 * branch in a paged fashion.
	 *
	 * <p>
	 * For example, calling {@code getCommitTimestampsPaged(10000, 100, 0, Order.DESCENDING)} will give the latest 100
	 * commit timestamps that have occurred before timestamp 10000. Calling
	 * {@code getCommitTimestampsPaged(123456, 200, 2, Order.DESCENDING} will return 200 commit timestamps, skipping the
	 * 400 latest commit timestamps, which are smaller than 123456.
	 *
	 * @param minTimestamp
	 *            The minimum timestamp to consider (inclusive). All lower timestamps will be excluded from the
	 *            pagination. Must be less than or equal to the timestamp of this transaction.
	 * @param maxTimestamp
	 *            The highest timestamp to consider (inclusive). All higher timestamps will be excluded from the
	 *            pagination. Must be less than or equal to the timestamp of this transaction.
	 * @param pageSize
	 *            The size of the page, i.e. the maximum number of elements allowed to be contained in the resulting
	 *            iterator. Must be greater than zero.
	 * @param pageIndex
	 *            The index of the page to retrieve. Must not be negative.
	 * @param order
	 *            The desired ordering for the commit timestamps
	 *
	 * @return An iterator that contains the commit timestamps for the requested page. Never <code>null</code>, may be
	 *         empty. If the requested page does not exist, this iterator will always be empty.
	 */
	public default Iterator<Long> getCommitTimestampsPaged(final long minTimestamp, final long maxTimestamp,
			final int pageSize, final int pageIndex, final Order order) {
		return this.getCommitTimestampsPaged(ChronoDBConstants.MASTER_BRANCH_IDENTIFIER, minTimestamp, maxTimestamp,
				pageSize, pageIndex, order);
	}

	/**
	 * Returns an iterator over commit timestamps in a paged fashion.
	 *
	 * <p>
	 * For example, calling {@code getCommitTimestampsPaged(10000, 100, 0, Order.DESCENDING)} will give the latest 100
	 * commit timestamps that have occurred before timestamp 10000. Calling
	 * {@code getCommitTimestampsPaged(123456, 200, 2, Order.DESCENDING} will return 200 commit timestamps, skipping the
	 * 400 latest commit timestamps, which are smaller than 123456.
	 *
	 * @param branch
	 *            The name of the branch to consider. Must not be <code>null</code>, must refer to an existing branch.
	 * @param minTimestamp
	 *            The minimum timestamp to consider (inclusive). All lower timestamps will be excluded from the
	 *            pagination. Must be less than or equal to the timestamp of this transaction.
	 * @param maxTimestamp
	 *            The highest timestamp to consider (inclusive). All higher timestamps will be excluded from the
	 *            pagination. Must be less than or equal to the timestamp of this transaction.
	 * @param pageSize
	 *            The size of the page, i.e. the maximum number of elements allowed to be contained in the resulting
	 *            iterator. Must be greater than zero.
	 * @param pageIndex
	 *            The index of the page to retrieve. Must not be negative.
	 * @param order
	 *            The desired ordering for the commit timestamps
	 *
	 * @return An iterator that contains the commit timestamps for the requested page. Never <code>null</code>, may be
	 *         empty. If the requested page does not exist, this iterator will always be empty.
	 */
	public Iterator<Long> getCommitTimestampsPaged(final String branch, final long minTimestamp,
			final long maxTimestamp, final int pageSize, final int pageIndex, final Order order);

	/**
	 * Returns an iterator over commit timestamps and associated metadata on the
	 * {@linkplain ChronoDBConstants#MASTER_BRANCH_IDENTIFIER master} branch in a paged fashion.
	 *
	 * <p>
	 * For example, calling {@code getCommitTimestampsPaged(10000, 100, 0, Order.DESCENDING)} will give the latest 100
	 * commit timestamps that have occurred before timestamp 10000. Calling
	 * {@code getCommitTimestampsPaged(123456, 200, 2, Order.DESCENDING} will return 200 commit timestamps, skipping the
	 * 400 latest commit timestamps, which are smaller than 123456.
	 *
	 * <p>
	 * The {@link Entry Entries} returned by the iterator always have the commit timestamp as their first component and
	 * the metadata associated with this commit as their second component. The second component can be <code>null</code>
	 * if the commit was executed without providing metadata.
	 *
	 * @param minTimestamp
	 *            The minimum timestamp to consider (inclusive). All lower timestamps will be excluded from the
	 *            pagination. Must be less than or equal to the timestamp of this transaction.
	 * @param maxTimestamp
	 *            The highest timestamp to consider. All higher timestamps will be excluded from the pagination. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param pageSize
	 *            The size of the page, i.e. the maximum number of elements allowed to be contained in the resulting
	 *            iterator. Must be greater than zero.
	 * @param pageIndex
	 *            The index of the page to retrieve. Must not be negative.
	 * @param order
	 *            The desired ordering for the commit timestamps
	 *
	 * @return An iterator that contains the commits for the requested page. Never <code>null</code>, may be empty. If
	 *         the requested page does not exist, this iterator will always be empty.
	 */
	public default Iterator<Entry<Long, Object>> getCommitMetadataPaged(final long minTimestamp,
			final long maxTimestamp, final int pageSize, final int pageIndex, final Order order) {
		return this.getCommitMetadataPaged(ChronoDBConstants.MASTER_BRANCH_IDENTIFIER, minTimestamp, maxTimestamp,
				pageSize, pageIndex, order);
	}

	/**
	 * Returns an iterator over commit timestamps and associated metadata in a paged fashion.
	 *
	 * <p>
	 * For example, calling {@code getCommitTimestampsPaged(10000, 100, 0, Order.DESCENDING)} will give the latest 100
	 * commit timestamps that have occurred before timestamp 10000. Calling
	 * {@code getCommitTimestampsPaged(123456, 200, 2, Order.DESCENDING} will return 200 commit timestamps, skipping the
	 * 400 latest commit timestamps, which are smaller than 123456.
	 *
	 * <p>
	 * The {@link Entry Entries} returned by the iterator always have the commit timestamp as their first component and
	 * the metadata associated with this commit as their second component. The second component can be <code>null</code>
	 * if the commit was executed without providing metadata.
	 *
	 * @param branch
	 *            The name of the branch to consider. Must not be <code>null</code>, must refer to an existing branch.
	 * @param minTimestamp
	 *            The minimum timestamp to consider (inclusive). All lower timestamps will be excluded from the
	 *            pagination. Must be less than or equal to the timestamp of this transaction.
	 * @param maxTimestamp
	 *            The highest timestamp to consider. All higher timestamps will be excluded from the pagination. Must be
	 *            less than or equal to the timestamp of this transaction.
	 * @param pageSize
	 *            The size of the page, i.e. the maximum number of elements allowed to be contained in the resulting
	 *            iterator. Must be greater than zero.
	 * @param pageIndex
	 *            The index of the page to retrieve. Must not be negative.
	 * @param order
	 *            The desired ordering for the commit timestamps
	 *
	 * @return An iterator that contains the commits for the requested page. Never <code>null</code>, may be empty. If
	 *         the requested page does not exist, this iterator will always be empty.
	 */
	public Iterator<Entry<Long, Object>> getCommitMetadataPaged(final String branch, final long minTimestamp,
			final long maxTimestamp, final int pageSize, final int pageIndex, final Order order);

	/**
	 * Counts the number of commit timestamps on the {@link ChronoDBConstants#MASTER_BRANCH_IDENTIFIER master} branch
	 * between <code>from</code> (inclusive) and <code>to</code> (inclusive).
	 *
	 * <p>
	 * If <code>from</code> is greater than <code>to</code>, this method will always return zero.
	 *
	 * @param from
	 *            The minimum timestamp to include in the search (inclusive). Must not be negative. Must be less than or
	 *            equal to the timestamp of this transaction.
	 * @param to
	 *            The maximum timestamp to include in the search (inclusive). Must not be negative. Must be less than or
	 *            equal to the timestamp of this transaction.
	 *
	 * @return The number of commits that have occurred in the specified time range. May be zero, but never negative.
	 */
	public default int countCommitTimestampsBetween(final long from, final long to) {
		return this.countCommitTimestampsBetween(ChronoDBConstants.MASTER_BRANCH_IDENTIFIER, from, to);
	}

	/**
	 * Counts the number of commit timestamps between <code>from</code> (inclusive) and <code>to</code> (inclusive).
	 *
	 * <p>
	 * If <code>from</code> is greater than <code>to</code>, this method will always return zero.
	 *
	 * @param branch
	 *            The name of the branch to consider. Must not be <code>null</code>, must refer to an existing branch.
	 * @param from
	 *            The minimum timestamp to include in the search (inclusive). Must not be negative. Must be less than or
	 *            equal to the timestamp of this transaction.
	 * @param to
	 *            The maximum timestamp to include in the search (inclusive). Must not be negative. Must be less than or
	 *            equal to the timestamp of this transaction.
	 *
	 * @return The number of commits that have occurred in the specified time range. May be zero, but never negative.
	 */
	public int countCommitTimestampsBetween(final String branch, long from, long to);

	/**
	 * Counts the total number of commit timestamps on the {@link ChronoDBConstants#MASTER_BRANCH_IDENTIFIER master}
	 * branch.
	 *
	 * @return The total number of commits in the graph.
	 */
	public default int countCommitTimestamps() {
		return this.countCommitTimestamps(ChronoDBConstants.MASTER_BRANCH_IDENTIFIER);
	}

	/**
	 * Counts the total number of commit timestamps in the graph.
	 *
	 * @param branch
	 *            The name of the branch to consider. Must not be <code>null</code>, must refer to an existing branch.
	 *
	 * @return The total number of commits in the graph.
	 */
	public int countCommitTimestamps(String branch);

	// =====================================================================================================================
	// INDEX MANAGEMENT
	// =====================================================================================================================

	/**
	 * Returns the index manager for this graph.
	 *
	 * <p>
	 * This will return the index manager for the <i>master</i> branch.
	 *
	 * @return The index manager. Never <code>null</code>.
	 */
	public ChronoGraphIndexManager getIndexManager();

	/**
	 * Returns The index manager for the given branch.
	 *
	 * @param branchName
	 *            The name of the branch to get the index manager for. Must not be <code>null</code>.
	 * @return The index manger for the given branch. Never <code>null</code>.
	 */
	public ChronoGraphIndexManager getIndexManager(String branchName);

	// =====================================================================================================================
	// BRANCH MANAGEMENT
	// =====================================================================================================================

	/**
	 * Returns the branch manager associated with this {@link ChronoGraph}.
	 *
	 * @return The branch manager. Never <code>null</code>.
	 */
	public ChronoGraphBranchManager getBranchManager();

	// =================================================================================================================
	// QUERY API
	// =================================================================================================================

	/**
	 * Starting point for queries in this {@link ChronoGraph} instance.
	 *
	 * <p>
	 * <b>NOTE:</b> This method requires an open transaction!
	 *
	 * <p>
	 * The returned object is a query builder that is supposed to be used e.g. like this:
	 *
	 * <pre>
	 * ChronoGraph g = ...;
	 * Set&lt;Vertex&gt; vertices = g.find().vertices().where("name").containsIgnoreCase("foo").toSet();
	 * </pre>
	 *
	 * You can also switch to a gremlin query from the builder:
	 *
	 * <pre>
	 * ChronoGraph g = ...;
	 * g.find().vertices().where("name").containsIgnoreCase("foo").toTraversal().outE()...
	 * </pre>
	 *
	 *
	 * @return The new query builder instance, intended for method chaining. Never <code>null</code>.
	 */
	public GraphQueryBuilderStarter find();

	// =====================================================================================================================
	// DUMP API
	// =====================================================================================================================

	/**
	 * Creates a database dump of the entire current database state.
	 *
	 * <p>
	 * Please note that the database is not available for write operations while the dump process is running. Read
	 * operations will work concurrently as usual.
	 *
	 * <p>
	 * <b>WARNING:</b> The file created by this process may be very large (several gigabytes), depending on the size of
	 * the database. It is the responsibility of the user of this API to ensure that enough disk space is available;
	 * this method does not perform any checks regarding disk space availability!
	 *
	 * <p>
	 * <b>WARNING:</b> The given file will be <b>overwritten</b> without further notice!
	 *
	 * @param dumpFile
	 *            The file to store the dump into. Must not be <code>null</code>. Must point to a file (not a
	 *            directory). The standard file extension <code>*.chronodump</code> is recommmended, but not required.
	 *            If the file does not exist, the file (and any missing parent directory) will be created.
	 * @param dumpOptions
	 *            The options to use for this dump (optional). Please refer to the documentation of the invididual
	 *            constants for details.
	 */
	public void writeDump(File dumpFile, DumpOption... dumpOptions);

	/**
	 * Reads the contents of the given dump file into this database.
	 *
	 * <p>
	 * This is a management operation; it completely locks the database. No concurrent writes or reads will be permitted
	 * while this operation is being executed.
	 *
	 * <p>
	 * <b>WARNING:</b> The current contents of the database will be <b>merged</b> with the contents of the dump! In case
	 * of conflicts, the data stored in the dump file will take precedence. It is <i>strongly recommended</i> to perform
	 * this operation only on an <b>empty</b> database!
	 *
	 * <p>
	 * <b>WARNING:</b> As this is a management operation, there is no rollback or undo option!
	 *
	 * @param dumpFile
	 *            The dump file to read the data from. Must not be <code>null</code>, must exist, and must point to a
	 *            file (not a directory).
	 * @param options
	 *            The options to use while reading (optional). May be empty.
	 */
	public void readDump(File dumpFile, DumpOption... options);

	// =====================================================================================================================
	// INTERNAL API
	// =====================================================================================================================

	/**
	 * Returns the {@link ChronoDB} instance that acts as the backing store for this {@link ChronoGraph}.
	 *
	 * @return The backing ChronoDB instance. Never <code>null</code>.
	 */
	public ChronoDB getBackingDB();

}
