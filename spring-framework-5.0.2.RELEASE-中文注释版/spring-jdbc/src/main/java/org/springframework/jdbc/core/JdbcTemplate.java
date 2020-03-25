/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.jdbc.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.BatchUpdateException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.SQLWarningException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.datasource.ConnectionProxy;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcAccessor;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.StringUtils;

/**
 * <b>This is the central class in the JDBC core package.</b>
 * It simplifies the use of JDBC and helps to avoid common errors.
 * It executes core JDBC workflow, leaving application code to provide SQL
 * and extract results. This class executes SQL queries or updates, initiating
 * iteration over ResultSets and catching JDBC exceptions and translating
 * them to the generic, more informative exception hierarchy defined in the
 * {@code org.springframework.dao} package.
 *
 * <p>Code using this class need only implement callback interfaces, giving
 * them a clearly defined contract. The {@link PreparedStatementCreator} callback
 * interface creates a prepared statement given a Connection, providing SQL and
 * any necessary parameters. The {@link ResultSetExtractor} interface extracts
 * values from a ResultSet. See also {@link PreparedStatementSetter} and
 * {@link RowMapper} for two popular alternative callback interfaces.
 *
 * <p>Can be used within a service implementation via direct instantiation
 * with a DataSource reference, or get prepared in an application context
 * and given to services as bean reference. Note: The DataSource should
 * always be configured as a bean in the application context, in the first case
 * given to the service directly, in the second case to the prepared template.
 *
 * <p>Because this class is parameterizable by the callback interfaces and
 * the {@link org.springframework.jdbc.support.SQLExceptionTranslator}
 * interface, there should be no need to subclass it.
 *
 * <p>All SQL operations performed by this class are logged at debug level,
 * using "org.springframework.jdbc.core.JdbcTemplate" as log category.
 *
 * <p><b>NOTE: An instance of this class is thread-safe once configured.</b>
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Thomas Risberg
 * @since May 3, 2001
 * @see PreparedStatementCreator
 * @see PreparedStatementSetter
 * @see CallableStatementCreator
 * @see PreparedStatementCallback
 * @see CallableStatementCallback
 * @see ResultSetExtractor
 * @see RowCallbackHandler
 * @see RowMapper
 * @see org.springframework.jdbc.support.SQLExceptionTranslator
 */
public class JdbcTemplate extends JdbcAccessor implements JdbcOperations {

	private static final String RETURN_RESULT_SET_PREFIX = "#result-set-";

	private static final String RETURN_UPDATE_COUNT_PREFIX = "#update-count-";


	/** If this variable is false, we will throw exceptions on SQL warnings */
	private boolean ignoreWarnings = true;

	/**
	 * If this variable is set to a non-negative value, it will be used for setting the
	 * fetchSize property on statements used for query processing.
	 */
	private int fetchSize = -1;

	/**
	 * If this variable is set to a non-negative value, it will be used for setting the
	 * maxRows property on statements used for query processing.
	 */
	private int maxRows = -1;

	/**
	 * If this variable is set to a non-negative value, it will be used for setting the
	 * queryTimeout property on statements used for query processing.
	 */
	private int queryTimeout = -1;

	/**
	 * If this variable is set to true, then all results checking will be bypassed for any
	 * callable statement processing. This can be used to avoid a bug in some older Oracle
	 * JDBC drivers like 10.1.0.2.
	 */
	private boolean skipResultsProcessing = false;

	/**
	 * If this variable is set to true then all results from a stored procedure call
	 * that don't have a corresponding SqlOutParameter declaration will be bypassed.
	 * All other results processing will be take place unless the variable
	 * {@code skipResultsProcessing} is set to {@code true}.
	 */
	private boolean skipUndeclaredResults = false;

	/**
	 * If this variable is set to true then execution of a CallableStatement will return
	 * the results in a Map that uses case insensitive names for the parameters.
	 */
	private boolean resultsMapCaseInsensitive = false;


	/**
	 * Construct a new JdbcTemplate for bean usage.
	 * <p>Note: The DataSource has to be set before using the instance.
	 * @see #setDataSource
	 */
	public JdbcTemplate() {
	}

	/**
	 * Construct a new JdbcTemplate, given a DataSource to obtain connections from.
	 * <p>Note: This will not trigger initialization of the exception translator.
	 * @param dataSource the JDBC DataSource to obtain connections from
	 */
	public JdbcTemplate(DataSource dataSource) {
		setDataSource(dataSource);
		afterPropertiesSet();
	}

	/**
	 * Construct a new JdbcTemplate, given a DataSource to obtain connections from.
	 * <p>Note: Depending on the "lazyInit" flag, initialization of the exception translator
	 * will be triggered.
	 * @param dataSource the JDBC DataSource to obtain connections from
	 * @param lazyInit whether to lazily initialize the SQLExceptionTranslator
	 */
	public JdbcTemplate(DataSource dataSource, boolean lazyInit) {
		setDataSource(dataSource);
		setLazyInit(lazyInit);
		afterPropertiesSet();
	}


	/**
	 * Set whether or not we want to ignore SQLWarnings.
	 * <p>Default is "true", swallowing and logging all warnings. Switch this flag
	 * to "false" to make the JdbcTemplate throw a SQLWarningException instead.
	 * @see java.sql.SQLWarning
	 * @see org.springframework.jdbc.SQLWarningException
	 * @see #handleWarnings
	 */
	public void setIgnoreWarnings(boolean ignoreWarnings) {
		this.ignoreWarnings = ignoreWarnings;
	}

	/**
	 * Return whether or not we ignore SQLWarnings.
	 */
	public boolean isIgnoreWarnings() {
		return this.ignoreWarnings;
	}

	/**
	 * Set the fetch size for this JdbcTemplate. This is important for processing large
	 * result sets: Setting this higher than the default value will increase processing
	 * speed at the cost of memory consumption; setting this lower can avoid transferring
	 * row data that will never be read by the application.
	 * <p>Default is -1, indicating to use the JDBC driver's default configuration
	 * (i.e. to not pass a specific fetch size setting on to the driver).
	 * <p>Note: As of 4.3, negative values other than -1 will get passed on to the
	 * driver, since e.g. MySQL supports special behavior for {@code Integer.MIN_VALUE}.
	 * @see java.sql.Statement#setFetchSize
	 */
	public void setFetchSize(int fetchSize) {
		this.fetchSize = fetchSize;
	}

	/**
	 * Return the fetch size specified for this JdbcTemplate.
	 */
	public int getFetchSize() {
		return this.fetchSize;
	}

	/**
	 * Set the maximum number of rows for this JdbcTemplate. This is important for
	 * processing subsets of large result sets, avoiding to read and hold the entire
	 * result set in the database or in the JDBC driver if we're never interested in
	 * the entire result in the first place (for example, when performing searches
	 * that might return a large number of matches).
	 * <p>Default is -1, indicating to use the JDBC driver's default configuration
	 * (i.e. to not pass a specific max rows setting on to the driver).
	 * <p>Note: As of 4.3, negative values other than -1 will get passed on to the
	 * driver, in sync with {@link #setFetchSize}'s support for special MySQL values.
	 * @see java.sql.Statement#setMaxRows
	 */
	public void setMaxRows(int maxRows) {
		this.maxRows = maxRows;
	}

	/**
	 * Return the maximum number of rows specified for this JdbcTemplate.
	 */
	public int getMaxRows() {
		return this.maxRows;
	}

	/**
	 * Set the query timeout for statements that this JdbcTemplate executes.
	 * <p>Default is -1, indicating to use the JDBC driver's default
	 * (i.e. to not pass a specific query timeout setting on the driver).
	 * <p>Note: Any timeout specified here will be overridden by the remaining
	 * transaction timeout when executing within a transaction that has a
	 * timeout specified at the transaction level.
	 * @see java.sql.Statement#setQueryTimeout
	 */
	public void setQueryTimeout(int queryTimeout) {
		this.queryTimeout = queryTimeout;
	}

	/**
	 * Return the query timeout for statements that this JdbcTemplate executes.
	 */
	public int getQueryTimeout() {
		return this.queryTimeout;
	}

	/**
	 * Set whether results processing should be skipped. Can be used to optimize callable
	 * statement processing when we know that no results are being passed back - the processing
	 * of out parameter will still take place. This can be used to avoid a bug in some older
	 * Oracle JDBC drivers like 10.1.0.2.
	 */
	public void setSkipResultsProcessing(boolean skipResultsProcessing) {
		this.skipResultsProcessing = skipResultsProcessing;
	}

	/**
	 * Return whether results processing should be skipped.
	 */
	public boolean isSkipResultsProcessing() {
		return this.skipResultsProcessing;
	}

	/**
	 * Set whether undeclared results should be skipped.
	 */
	public void setSkipUndeclaredResults(boolean skipUndeclaredResults) {
		this.skipUndeclaredResults = skipUndeclaredResults;
	}

	/**
	 * Return whether undeclared results should be skipped.
	 */
	public boolean isSkipUndeclaredResults() {
		return this.skipUndeclaredResults;
	}

	/**
	 * Set whether execution of a CallableStatement will return the results in a Map
	 * that uses case insensitive names for the parameters.
	 */
	public void setResultsMapCaseInsensitive(boolean resultsMapCaseInsensitive) {
		this.resultsMapCaseInsensitive = resultsMapCaseInsensitive;
	}

	/**
	 * Return whether execution of a CallableStatement will return the results in a Map
	 * that uses case insensitive names for the parameters.
	 */
	public boolean isResultsMapCaseInsensitive() {
		return this.resultsMapCaseInsensitive;
	}


	//-------------------------------------------------------------------------
	// Methods dealing with a plain java.sql.Connection
	//-------------------------------------------------------------------------

	@Override
	@Nullable
	public <T> T execute(ConnectionCallback<T> action) throws DataAccessException {
		Assert.notNull(action, "Callback object must not be null");

		Connection con = DataSourceUtils.getConnection(obtainDataSource());
		try {
			// Create close-suppressing Connection proxy, also preparing returned Statements.
			Connection conToUse = createConnectionProxy(con);
			return action.doInConnection(conToUse);
		}
		catch (SQLException ex) {
			// Release Connection early, to avoid potential connection pool deadlock
			// in the case when the exception translator hasn't been initialized yet.
			String sql = getSql(action);
			DataSourceUtils.releaseConnection(con, getDataSource());
			con = null;
			throw translateException("ConnectionCallback", sql, ex);
		}
		finally {
			DataSourceUtils.releaseConnection(con, getDataSource());
		}
	}

	/**
	 * Create a close-suppressing proxy for the given JDBC Connection.
	 * Called by the {@code execute} method.
	 * <p>The proxy also prepares returned JDBC Statements, applying
	 * statement settings such as fetch size, max rows, and query timeout.
	 * @param con the JDBC Connection to create a proxy for
	 * @return the Connection proxy
	 * @see java.sql.Connection#close()
	 * @see #execute(ConnectionCallback)
	 * @see #applyStatementSettings
	 */
	protected Connection createConnectionProxy(Connection con) {
		return (Connection) Proxy.newProxyInstance(
				ConnectionProxy.class.getClassLoader(),
				new Class<?>[] {ConnectionProxy.class},
				new CloseSuppressingInvocationHandler(con));
	}


	//-------------------------------------------------------------------------
	// Methods dealing with static SQL (java.sql.Statement)
	//-------------------------------------------------------------------------
//		这个 exexute 与之前的 execute 并无太大差别，都是做一些常规的处理，诸如获取连接、释
//	连接等，但是，有一地方是不一样的，就是 statement 的创建。这里直接使用 connection 创建,
//	而带有参数的SQL 使用的是 PreparedStatementCreator 类来创建的。一个是普通的Statement,
//	另一个是 PreparedStatement,两者究竟是何区别呢？
//		PreparedStatement 接口継承 Statement ,并与之在两方面有所不同。
//				a. PreparedStatement 实例包含已编译的 SQL 语句.这就是使语句“准备好”.包舍于 PreparedStatement 对象中的SQL语句可具有一个或多个 IN参数,
//			IN参教的值在 SQL 语句创建时未被指定.相反的，该语句为每个IN参数保留一个问号（"?"）作为占位符.
//			每个问号的值必须在该语句执行之前，通过适当的setXXX方法来提供.
//				b. 由于 PreparedStatement 对象已预编译过，所以其执行速度要快于 Statement 对象.因此，多次执行的SQL语句经常创建为 PreparedStatement 对象，
//				以提高效率.

	@Override
	@Nullable
	public <T> T execute(StatementCallback<T> action) throws DataAccessException {
		Assert.notNull(action, "Callback object must not be null");

		Connection con = DataSourceUtils.getConnection(obtainDataSource());
		Statement stmt = null;
		try {
			stmt = con.createStatement();
			applyStatementSettings(stmt);
			T result = action.doInStatement(stmt);
			handleWarnings(stmt);
			return result;
		}
		catch (SQLException ex) {
			// Release Connection early, to avoid potential connection pool deadlock
			// in the case when the exception translator hasn't been initialized yet.
			String sql = getSql(action);
			JdbcUtils.closeStatement(stmt);
			stmt = null;
			DataSourceUtils.releaseConnection(con, getDataSource());
			con = null;
			throw translateException("StatementCallback", sql, ex);
		}
		finally {
			JdbcUtils.closeStatement(stmt);
			DataSourceUtils.releaseConnection(con, getDataSource());
		}
	}

	@Override
	public void execute(final String sql) throws DataAccessException {
		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL statement [" + sql + "]");
		}

		class ExecuteStatementCallback implements StatementCallback<Object>, SqlProvider {
			@Override
			@Nullable
			public Object doInStatement(Statement stmt) throws SQLException {
				stmt.execute(sql);
				return null;
			}
			@Override
			public String getSql() {
				return sql;
			}
		}

		execute(new ExecuteStatementCallback());
	}

	@Override
	@Nullable
	public <T> T query(final String sql, final ResultSetExtractor<T> rse) throws DataAccessException {
		Assert.notNull(sql, "SQL must not be null");
		Assert.notNull(rse, "ResultSetExtractor must not be null");
		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL query [" + sql + "]");
		}

//			与之前的 query 方法最大的不同是少了参数及参数类型的传递，自然也少了 PreparedStatementsetter类型的封装。
//		既然少了 PreparedStatementSetter类型的传入，调用的execute方法自然也会有所改变了。
		class QueryStatementCallback implements StatementCallback<T>, SqlProvider {
			@Override
			@Nullable
			public T doInStatement(Statement stmt) throws SQLException {
				ResultSet rs = null;
				try {
					rs = stmt.executeQuery(sql);
					return rse.extractData(rs);
				}
				finally {
					JdbcUtils.closeResultSet(rs);
				}
			}
			@Override
			public String getSql() {
				return sql;
			}
		}

		return execute(new QueryStatementCallback());
	}

//		之前讲了 update方法以及query 方法，使用这两个函数示例的 SQL 都是带有参数的，也就是带有"?"的，
//	那么还有另一种情况是不带有"?"的，Spring中使用的是另一种处理方式。例如：
//		Llst<User> list = jdbcTemplate.query(" select * from user", new UserRowMapper());
	@Override
	public void query(String sql, RowCallbackHandler rch) throws DataAccessException {
		query(sql, new RowCallbackHandlerResultSetExtractor(rch));
	}

	@Override
	public <T> List<T> query(String sql, RowMapper<T> rowMapper) throws DataAccessException {
		return result(query(sql, new RowMapperResultSetExtractor<>(rowMapper)));
	}

	@Override
	public Map<String, Object> queryForMap(String sql) throws DataAccessException {
		return result(queryForObject(sql, getColumnMapRowMapper()));
	}

	@Override
	@Nullable
	public <T> T queryForObject(String sql, RowMapper<T> rowMapper) throws DataAccessException {
		List<T> results = query(sql, rowMapper);
		return DataAccessUtils.nullableSingleResult(results);
	}

	@Override
	@Nullable
	public <T> T queryForObject(String sql, Class<T> requiredType) throws DataAccessException {
		return queryForObject(sql, getSingleColumnRowMapper(requiredType));
	}

	@Override
	public <T> List<T> queryForList(String sql, Class<T> elementType) throws DataAccessException {
		return query(sql, getSingleColumnRowMapper(elementType));
	}

	@Override
	public List<Map<String, Object>> queryForList(String sql) throws DataAccessException {
		return query(sql, getColumnMapRowMapper());
	}

	@Override
	public SqlRowSet queryForRowSet(String sql) throws DataAccessException {
		return result(query(sql, new SqlRowSetResultSetExtractor()));
	}

	@Override
	public int update(final String sql) throws DataAccessException {
		Assert.notNull(sql, "SQL must not be null");
		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL update [" + sql + "]");
		}

		class UpdateStatementCallback implements StatementCallback<Integer>, SqlProvider {
			@Override
			public Integer doInStatement(Statement stmt) throws SQLException {
				int rows = stmt.executeUpdate(sql);
				if (logger.isDebugEnabled()) {
					logger.debug("SQL update affected " + rows + " rows");
				}
				return rows;
			}
			@Override
			public String getSql() {
				return sql;
			}
		}

		return updateCount(execute(new UpdateStatementCallback()));
	}

	@Override
	public int[] batchUpdate(final String... sql) throws DataAccessException {
		Assert.notEmpty(sql, "SQL array must not be empty");
		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL batch update of " + sql.length + " statements");
		}

		class BatchUpdateStatementCallback implements StatementCallback<int[]>, SqlProvider {

			@Nullable
			private String currSql;

			@Override
			public int[] doInStatement(Statement stmt) throws SQLException, DataAccessException {
				int[] rowsAffected = new int[sql.length];
				if (JdbcUtils.supportsBatchUpdates(stmt.getConnection())) {
					for (String sqlStmt : sql) {
						this.currSql = appendSql(this.currSql, sqlStmt);
						stmt.addBatch(sqlStmt);
					}
					try {
						rowsAffected = stmt.executeBatch();
					}
					catch (BatchUpdateException ex) {
						String batchExceptionSql = null;
						for (int i = 0; i < ex.getUpdateCounts().length; i++) {
							if (ex.getUpdateCounts()[i] == Statement.EXECUTE_FAILED) {
								batchExceptionSql = appendSql(batchExceptionSql, sql[i]);
							}
						}
						if (StringUtils.hasLength(batchExceptionSql)) {
							this.currSql = batchExceptionSql;
						}
						throw ex;
					}
				}
				else {
					for (int i = 0; i < sql.length; i++) {
						this.currSql = sql[i];
						if (!stmt.execute(sql[i])) {
							rowsAffected[i] = stmt.getUpdateCount();
						}
						else {
							throw new InvalidDataAccessApiUsageException("Invalid batch SQL statement: " + sql[i]);
						}
					}
				}
				return rowsAffected;
			}

			private String appendSql(@Nullable String sql, String statement) {
				return (StringUtils.isEmpty(sql) ? statement : sql + "; " + statement);
			}

			@Override
			@Nullable
			public String getSql() {
				return this.currSql;
			}
		}

		int[] result = execute(new BatchUpdateStatementCallback());
		Assert.state(result != null, "No update counts");
		return result;
	}


	//-------------------------------------------------------------------------
	// Methods dealing with prepared statements
	//-------------------------------------------------------------------------
//		execute	作为效据库操作的核心入口，将大多数数据库操作相同的步骤统一封装，而将个性
//	化的操作使用参数PreparedStatementCallback进行回调。
	@Override
	@Nullable
	public <T> T execute(PreparedStatementCreator psc, PreparedStatementCallback<T> action)
			throws DataAccessException {

		Assert.notNull(psc, "PreparedStatementCreator must not be null");
		Assert.notNull(action, "Callback object must not be null");
		if (logger.isDebugEnabled()) {
			String sql = getSql(psc);
			logger.debug("Executing prepared SQL statement" + (sql != null ? " [" + sql + "]" : ""));
		}
		///1. 获取数据库连接
		Connection con = DataSourceUtils.getConnection(obtainDataSource());
		PreparedStatement ps = null;
		try {
			ps = psc.createPreparedStatement(con);
			///2.  应用用户设定的输入参数 eg:  applying statement settings such as fetch size, max rows, and query timeout.
			applyStatementSettings(ps);
			/// 调用回调参数
			T result = action.doInPreparedStatement(ps);
			/// 警告处理
			handleWarnings(ps);
			return result;
		}
		catch (SQLException ex) {
			// Release Connection early, to avoid potential connection pool deadlock
			// in the case when the exception translator hasn't been initialized yet.
			/// 释放数据库连接，避免当异常转换器没有被初始化的时候出现潜在的连接池死锁
			if (psc instanceof ParameterDisposer) {
				((ParameterDisposer) psc).cleanupParameters();
			}
			String sql = getSql(psc);
			JdbcUtils.closeStatement(ps);
			ps = null;
			DataSourceUtils.releaseConnection(con, getDataSource());
			con = null;
			throw translateException("PreparedStatementCallback", sql, ex);
		}
		finally {
			if (psc instanceof ParameterDisposer) {
				((ParameterDisposer) psc).cleanupParameters();
			}
			JdbcUtils.closeStatement(ps);
			DataSourceUtils.releaseConnection(con, getDataSource());
		}
	}

	@Override
	@Nullable
	public <T> T execute(String sql, PreparedStatementCallback<T> action) throws DataAccessException {
		return execute(new SimplePreparedStatementCreator(sql), action);
	}

	/**
	 * Query using a prepared statement, allowing for a PreparedStatementCreator
	 * and a PreparedStatementSetter. Most other query methods use this method,
	 * but application code will always work with either a creator or a setter.
	 * @param psc Callback handler that can create a PreparedStatement given a
	 * Connection
	 * @param pss object that knows how to set values on the prepared statement.
	 * If this is null, the SQL will be assumed to contain no bind parameters.
	 * @param rse object that will extract results.
	 * @return an arbitrary result object, as returned by the ResultSetExtractor
	 * @throws DataAccessException if there is any problem
	 */
//		可以看到整体套路与 update 差不多的，只不过在回调类 PreparedStatementCallback 的实现
//	中使用的是 ps.executeQuery 渺行査询操作，而且在返回方法上也做了一些额外的处理。
//		rse.extractDaia(rsToUse) 方法负责将结果进行封装并转换至POJO, rse当前代表的类为
//	RowMapperResultSetExtractor,而在构造 RowMapperResultSetExtractor 的时候我们又将自定义
//	的rowMapper设置了进去。调用代码如下：
	@Nullable
	public <T> T query(
			PreparedStatementCreator psc, @Nullable final PreparedStatementSetter pss, final ResultSetExtractor<T> rse)
			throws DataAccessException {

		Assert.notNull(rse, "ResultSetExtractor must not be null");
		logger.debug("Executing prepared SQL query");

		return execute(psc, new PreparedStatementCallback<T>() {
			@Override
			@Nullable
			public T doInPreparedStatement(PreparedStatement ps) throws SQLException {
				ResultSet rs = null;
				try {
					if (pss != null) {
						pss.setValues(ps);
					}
					rs = ps.executeQuery();
					return rse.extractData(rs);
				}
				finally {
					JdbcUtils.closeResultSet(rs);
					if (pss instanceof ParameterDisposer) {
						((ParameterDisposer) pss).cleanupParameters();
					}
				}
			}
		});
	}

	@Override
	@Nullable
	public <T> T query(PreparedStatementCreator psc, ResultSetExtractor<T> rse) throws DataAccessException {
		return query(psc, null, rse);
	}

	@Override
	@Nullable
	public <T> T query(String sql, @Nullable PreparedStatementSetter pss, ResultSetExtractor<T> rse) throws DataAccessException {
		return query(new SimplePreparedStatementCreator(sql), pss, rse);
	}

/// 例如：	List<User> list = jdbcTemplate.query("select *  from user where age=?", new Object[]{20},new int[]{java.sql.Types.INTEGER} ,new UserRowMapper());
	@Override
	@Nullable
	public <T> T query(String sql, Object[] args, int[] argTypes, ResultSetExtractor<T> rse) throws DataAccessException {
		/// 上面歯敷中与 update 方法中邨冋样使用了 newArgTypePreparedStatementSetter
		return query(sql, newArgTypePreparedStatementSetter(args, argTypes), rse);
	}

	@Override
	@Nullable
	public <T> T query(String sql, @Nullable Object[] args, ResultSetExtractor<T> rse) throws DataAccessException {
		return query(sql, newArgPreparedStatementSetter(args), rse);
	}

	@Override
	@Nullable
	public <T> T query(String sql, ResultSetExtractor<T> rse, @Nullable Object... args) throws DataAccessException {
		return query(sql, newArgPreparedStatementSetter(args), rse);
	}

	@Override
	public void query(PreparedStatementCreator psc, RowCallbackHandler rch) throws DataAccessException {
		query(psc, new RowCallbackHandlerResultSetExtractor(rch));
	}

	@Override
	public void query(String sql, @Nullable PreparedStatementSetter pss, RowCallbackHandler rch) throws DataAccessException {
		query(sql, pss, new RowCallbackHandlerResultSetExtractor(rch));
	}

	@Override
	public void query(String sql, Object[] args, int[] argTypes, RowCallbackHandler rch) throws DataAccessException {
		query(sql, newArgTypePreparedStatementSetter(args, argTypes), rch);
	}

	@Override
	public void query(String sql, Object[] args, RowCallbackHandler rch) throws DataAccessException {
		query(sql, newArgPreparedStatementSetter(args), rch);
	}

	@Override
	public void query(String sql, RowCallbackHandler rch, @Nullable Object... args) throws DataAccessException {
		query(sql, newArgPreparedStatementSetter(args), rch);
	}

	@Override
	public <T> List<T> query(PreparedStatementCreator psc, RowMapper<T> rowMapper) throws DataAccessException {
		return result(query(psc, new RowMapperResultSetExtractor<>(rowMapper)));
	}

	@Override
	public <T> List<T> query(String sql, @Nullable PreparedStatementSetter pss, RowMapper<T> rowMapper) throws DataAccessException {
		return result(query(sql, pss, new RowMapperResultSetExtractor<>(rowMapper)));
	}

	@Override
	public <T> List<T> query(String sql, Object[] args, int[] argTypes, RowMapper<T> rowMapper) throws DataAccessException {
		return result(query(sql, args, argTypes, new RowMapperResultSetExtractor<>(rowMapper)));
	}

	@Override
	public <T> List<T> query(String sql, @Nullable Object[] args, RowMapper<T> rowMapper) throws DataAccessException {
		return result(query(sql, args, new RowMapperResultSetExtractor<>(rowMapper)));
	}

	@Override
	public <T> List<T> query(String sql, RowMapper<T> rowMapper, @Nullable Object... args) throws DataAccessException {
		return result(query(sql, args, new RowMapperResultSetExtractor<>(rowMapper)));
	}

	@Override
	@Nullable
	public <T> T queryForObject(String sql, Object[] args, int[] argTypes, RowMapper<T> rowMapper)
			throws DataAccessException {

		List<T> results = query(sql, args, argTypes, new RowMapperResultSetExtractor<>(rowMapper, 1));
		return DataAccessUtils.nullableSingleResult(results);
	}

	@Override
	@Nullable
	public <T> T queryForObject(String sql, @Nullable Object[] args, RowMapper<T> rowMapper) throws DataAccessException {
		List<T> results = query(sql, args, new RowMapperResultSetExtractor<>(rowMapper, 1));
		return DataAccessUtils.nullableSingleResult(results);
	}

	@Override
	@Nullable
	public <T> T queryForObject(String sql, RowMapper<T> rowMapper, @Nullable Object... args) throws DataAccessException {
		List<T> results = query(sql, args, new RowMapperResultSetExtractor<>(rowMapper, 1));
		return DataAccessUtils.nullableSingleResult(results);
	}

	@Override
	@Nullable
	public <T> T queryForObject(String sql, Object[] args, int[] argTypes, Class<T> requiredType)
			throws DataAccessException {

		return queryForObject(sql, args, argTypes, getSingleColumnRowMapper(requiredType));
	}

	@Override
	public <T> T queryForObject(String sql, Object[] args, Class<T> requiredType) throws DataAccessException {
		return queryForObject(sql, args, getSingleColumnRowMapper(requiredType));
	}

	@Override
	public <T> T queryForObject(String sql, Class<T> requiredType, @Nullable Object... args) throws DataAccessException {
		return queryForObject(sql, args, getSingleColumnRowMapper(requiredType));
	}

	@Override
	public Map<String, Object> queryForMap(String sql, Object[] args, int[] argTypes) throws DataAccessException {
		return result(queryForObject(sql, args, argTypes, getColumnMapRowMapper()));
	}

	@Override
	public Map<String, Object> queryForMap(String sql, @Nullable Object... args) throws DataAccessException {
		return result(queryForObject(sql, args, getColumnMapRowMapper()));
	}

	@Override
	public <T> List<T> queryForList(String sql, Object[] args, int[] argTypes, Class<T> elementType) throws DataAccessException {
		return query(sql, args, argTypes, getSingleColumnRowMapper(elementType));
	}

	@Override
	public <T> List<T> queryForList(String sql, Object[] args, Class<T> elementType) throws DataAccessException {
		return query(sql, args, getSingleColumnRowMapper(elementType));
	}

	@Override
	public <T> List<T> queryForList(String sql, Class<T> elementType, @Nullable Object... args) throws DataAccessException {
		return query(sql, args, getSingleColumnRowMapper(elementType));
	}

	@Override
	public List<Map<String, Object>> queryForList(String sql, Object[] args, int[] argTypes) throws DataAccessException {
		return query(sql, args, argTypes, getColumnMapRowMapper());
	}

	@Override
	public List<Map<String, Object>> queryForList(String sql, @Nullable Object... args) throws DataAccessException {
		return query(sql, args, getColumnMapRowMapper());
	}

	@Override
	public SqlRowSet queryForRowSet(String sql, Object[] args, int[] argTypes) throws DataAccessException {
		return result(query(sql, args, argTypes, new SqlRowSetResultSetExtractor()));
	}

	@Override
	public SqlRowSet queryForRowSet(String sql, @Nullable Object... args) throws DataAccessException {
		return result(query(sql, args, new SqlRowSetResultSetExtractor()));
	}

//			PreparedStatementCallback 作为一个接口，其中只有一个函数 doInPreparedStatement,
//	这个函数是用于调用通用方法execute的时候无法处理的一些个性化处理方法，在update中的函数实现：
//		其中用于真正执行SQL的 ps.executeUpdat e没有太多需要讲解的，因为我们平时在直接使
//	用JDBC方式进行调用的时候会经常使用此方法。但是，对于设置输入参数的函数pss.setValues(ps),我们有必要去深入研究一下。
//	在没有分析源码之前，我们至少可以知道其功能，不妨再回顾下 Spring 中使用 SQL 的执行过程，直接使用：
//	jdbcTemplate.update("insert into user(name,age,sex)values(?,?, ?)",
//			new Object[] { user.getName(), user.getAge(),user.getSex()) }, new int[] { java.sql.Types.VARCHAR, java.sql.Types.INTEGER, java.sql.Types.VARCHAR });
//		首先，所有的操作都是以 pss.setValues(ps) 为入口的。还记得我们之前的分析路程吗？这个
//	pss所代表的当前类正是 ArgumentTypePreparedStatementSetter。 其中的 setValues 如下；
	protected int update(final PreparedStatementCreator psc, @Nullable final PreparedStatementSetter pss)
			throws DataAccessException {

		logger.debug("Executing prepared SQL update");

		return updateCount(execute(psc, ps -> {
			try {
				if (pss != null) {
					///	设置 PreparedStatement 所需全部参数
					pss.setValues(ps);
				}
				/// 执行更新操作
				int rows = ps.executeUpdate();
				if (logger.isDebugEnabled()) {
					logger.debug("SQL update affected " + rows + " rows");
				}
				return rows;
			}
			finally {
				if (pss instanceof ParameterDisposer) {
					((ParameterDisposer) pss).cleanupParameters();
				}
			}
		}));
	}

	@Override
	public int update(PreparedStatementCreator psc) throws DataAccessException {
		return update(psc, (PreparedStatementSetter) null);
	}

	@Override
	public int update(final PreparedStatementCreator psc, final KeyHolder generatedKeyHolder)
			throws DataAccessException {

		Assert.notNull(generatedKeyHolder, "KeyHolder must not be null");
		logger.debug("Executing SQL update and returning generated keys");

		return updateCount(execute(psc, ps -> {
			int rows = ps.executeUpdate();
			List<Map<String, Object>> generatedKeys = generatedKeyHolder.getKeyList();
			generatedKeys.clear();
			ResultSet keys = ps.getGeneratedKeys();
			if (keys != null) {
				try {
					RowMapperResultSetExtractor<Map<String, Object>> rse =
							new RowMapperResultSetExtractor<>(getColumnMapRowMapper(), 1);
					generatedKeys.addAll(result(rse.extractData(keys)));
				}
				finally {
					JdbcUtils.closeResultSet(keys);
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("SQL update affected " + rows + " rows and returned " + generatedKeys.size() + " keys");
			}
			return rows;
		}));
	}

	@Override
	public int update(String sql, @Nullable PreparedStatementSetter pss) throws DataAccessException {
		/// 使用 SimplePreparedStatementCreator 对 SQL 进行封装
		return update(new SimplePreparedStatementCreator(sql), pss);
	}

	@Override
	public int update(String sql, Object[] args, int[] argTypes) throws DataAccessException {
		///	newArgTypePreparedStatementSetter 对参数与参数类型进行封装
		return update(sql, newArgTypePreparedStatementSetter(args, argTypes));
	}

	@Override
	public int update(String sql, @Nullable Object... args) throws DataAccessException {
		return update(sql, newArgPreparedStatementSetter(args));
	}

	@Override
	public int[] batchUpdate(String sql, final BatchPreparedStatementSetter pss) throws DataAccessException {
		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL batch update [" + sql + "]");
		}

		int[] result = execute(sql, (PreparedStatementCallback<int[]>) ps -> {
			try {
				int batchSize = pss.getBatchSize();
				InterruptibleBatchPreparedStatementSetter ipss =
						(pss instanceof InterruptibleBatchPreparedStatementSetter ?
						(InterruptibleBatchPreparedStatementSetter) pss : null);
				if (JdbcUtils.supportsBatchUpdates(ps.getConnection())) {
					for (int i = 0; i < batchSize; i++) {
						pss.setValues(ps, i);
						if (ipss != null && ipss.isBatchExhausted(i)) {
							break;
						}
						ps.addBatch();
					}
					return ps.executeBatch();
				}
				else {
					List<Integer> rowsAffected = new ArrayList<>();
					for (int i = 0; i < batchSize; i++) {
						pss.setValues(ps, i);
						if (ipss != null && ipss.isBatchExhausted(i)) {
							break;
						}
						rowsAffected.add(ps.executeUpdate());
					}
					int[] rowsAffectedArray = new int[rowsAffected.size()];
					for (int i = 0; i < rowsAffectedArray.length; i++) {
						rowsAffectedArray[i] = rowsAffected.get(i);
					}
					return rowsAffectedArray;
				}
			}
			finally {
				if (pss instanceof ParameterDisposer) {
					((ParameterDisposer) pss).cleanupParameters();
				}
			}
		});

		Assert.state(result != null, "No result array");
		return result;
	}

	@Override
	public int[] batchUpdate(String sql, List<Object[]> batchArgs) throws DataAccessException {
		return batchUpdate(sql, batchArgs, new int[0]);
	}

	@Override
	public int[] batchUpdate(String sql, List<Object[]> batchArgs, int[] argTypes) throws DataAccessException {
		return BatchUpdateUtils.executeBatchUpdate(sql, batchArgs, argTypes, this);
	}

	@Override
	public <T> int[][] batchUpdate(String sql, final Collection<T> batchArgs, final int batchSize,
			final ParameterizedPreparedStatementSetter<T> pss) throws DataAccessException {

		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL batch update [" + sql + "] with a batch size of " + batchSize);
		}
		int[][] result = execute(sql, (PreparedStatementCallback<int[][]>) ps -> {
			List<int[]> rowsAffected = new ArrayList<>();
			try {
				boolean batchSupported = true;
				if (!JdbcUtils.supportsBatchUpdates(ps.getConnection())) {
					batchSupported = false;
					logger.warn("JDBC Driver does not support Batch updates; resorting to single statement execution");
				}
				int n = 0;
				for (T obj : batchArgs) {
					pss.setValues(ps, obj);
					n++;
					if (batchSupported) {
						ps.addBatch();
						if (n % batchSize == 0 || n == batchArgs.size()) {
							if (logger.isDebugEnabled()) {
								int batchIdx = (n % batchSize == 0) ? n / batchSize : (n / batchSize) + 1;
								int items = n - ((n % batchSize == 0) ? n / batchSize - 1 : (n / batchSize)) * batchSize;
								logger.debug("Sending SQL batch update #" + batchIdx + " with " + items + " items");
							}
							rowsAffected.add(ps.executeBatch());
						}
					}
					else {
						int i = ps.executeUpdate();
						rowsAffected.add(new int[] {i});
					}
				}
				int[][] result1 = new int[rowsAffected.size()][];
				for (int i = 0; i < result1.length; i++) {
					result1[i] = rowsAffected.get(i);
				}
				return result1;
			}
			finally {
				if (pss instanceof ParameterDisposer) {
					((ParameterDisposer) pss).cleanupParameters();
				}
			}
		});

		Assert.state(result != null, "No result array");
		return result;
	}

	//-------------------------------------------------------------------------
	// Methods dealing with callable statements
	//-------------------------------------------------------------------------

	@Override
	@Nullable
	public <T> T execute(CallableStatementCreator csc, CallableStatementCallback<T> action)
			throws DataAccessException {

		Assert.notNull(csc, "CallableStatementCreator must not be null");
		Assert.notNull(action, "Callback object must not be null");
		if (logger.isDebugEnabled()) {
			String sql = getSql(csc);
			logger.debug("Calling stored procedure" + (sql != null ? " [" + sql  + "]" : ""));
		}

		Connection con = DataSourceUtils.getConnection(obtainDataSource());
		CallableStatement cs = null;
		try {
			cs = csc.createCallableStatement(con);
			applyStatementSettings(cs);
			T result = action.doInCallableStatement(cs);
			handleWarnings(cs);
			return result;
		}
		catch (SQLException ex) {
			// Release Connection early, to avoid potential connection pool deadlock
			// in the case when the exception translator hasn't been initialized yet.
			if (csc instanceof ParameterDisposer) {
				((ParameterDisposer) csc).cleanupParameters();
			}
			String sql = getSql(csc);
			JdbcUtils.closeStatement(cs);
			cs = null;
			DataSourceUtils.releaseConnection(con, getDataSource());
			con = null;
			throw translateException("CallableStatementCallback", sql, ex);
		}
		finally {
			if (csc instanceof ParameterDisposer) {
				((ParameterDisposer) csc).cleanupParameters();
			}
			JdbcUtils.closeStatement(cs);
			DataSourceUtils.releaseConnection(con, getDataSource());
		}
	}

	@Override
	@Nullable
	public <T> T execute(String callString, CallableStatementCallback<T> action) throws DataAccessException {
		return execute(new SimpleCallableStatementCreator(callString), action);
	}

	@Override
	public Map<String, Object> call(CallableStatementCreator csc, List<SqlParameter> declaredParameters)
			throws DataAccessException {

		final List<SqlParameter> updateCountParameters = new ArrayList<>();
		final List<SqlParameter> resultSetParameters = new ArrayList<>();
		final List<SqlParameter> callParameters = new ArrayList<>();

		for (SqlParameter parameter : declaredParameters) {
			if (parameter.isResultsParameter()) {
				if (parameter instanceof SqlReturnResultSet) {
					resultSetParameters.add(parameter);
				}
				else {
					updateCountParameters.add(parameter);
				}
			}
			else {
				callParameters.add(parameter);
			}
		}

		Map<String, Object> result = execute(csc, cs -> {
			boolean retVal = cs.execute();
			int updateCount = cs.getUpdateCount();
			if (logger.isDebugEnabled()) {
				logger.debug("CallableStatement.execute() returned '" + retVal + "'");
				logger.debug("CallableStatement.getUpdateCount() returned " + updateCount);
			}
			Map<String, Object> returnedResults = createResultsMap();
			if (retVal || updateCount != -1) {
				returnedResults.putAll(extractReturnedResults(cs, updateCountParameters, resultSetParameters, updateCount));
			}
			returnedResults.putAll(extractOutputParameters(cs, callParameters));
			return returnedResults;
		});

		Assert.state(result != null, "No result map");
		return result;
	}

	/**
	 * Extract returned ResultSets from the completed stored procedure.
	 * @param cs JDBC wrapper for the stored procedure
	 * @param updateCountParameters Parameter list of declared update count parameters for the stored procedure
	 * @param resultSetParameters Parameter list of declared resultSet parameters for the stored procedure
	 * @return Map that contains returned results
	 */
	protected Map<String, Object> extractReturnedResults(CallableStatement cs,
			@Nullable List<SqlParameter> updateCountParameters, @Nullable List<SqlParameter> resultSetParameters,
			int updateCount) throws SQLException {

		Map<String, Object> returnedResults = new HashMap<>();
		int rsIndex = 0;
		int updateIndex = 0;
		boolean moreResults;
		if (!this.skipResultsProcessing) {
			do {
				if (updateCount == -1) {
					if (resultSetParameters != null && resultSetParameters.size() > rsIndex) {
						SqlReturnResultSet declaredRsParam = (SqlReturnResultSet) resultSetParameters.get(rsIndex);
						returnedResults.putAll(processResultSet(cs.getResultSet(), declaredRsParam));
						rsIndex++;
					}
					else {
						if (!this.skipUndeclaredResults) {
							String rsName = RETURN_RESULT_SET_PREFIX + (rsIndex + 1);
							SqlReturnResultSet undeclaredRsParam = new SqlReturnResultSet(rsName, getColumnMapRowMapper());
							if (logger.isDebugEnabled()) {
								logger.debug("Added default SqlReturnResultSet parameter named '" + rsName + "'");
							}
							returnedResults.putAll(processResultSet(cs.getResultSet(), undeclaredRsParam));
							rsIndex++;
						}
					}
				}
				else {
					if (updateCountParameters != null && updateCountParameters.size() > updateIndex) {
						SqlReturnUpdateCount ucParam = (SqlReturnUpdateCount) updateCountParameters.get(updateIndex);
						String declaredUcName = ucParam.getName();
						returnedResults.put(declaredUcName, updateCount);
						updateIndex++;
					}
					else {
						if (!this.skipUndeclaredResults) {
							String undeclaredName = RETURN_UPDATE_COUNT_PREFIX + (updateIndex + 1);
							if (logger.isDebugEnabled()) {
								logger.debug("Added default SqlReturnUpdateCount parameter named '" + undeclaredName + "'");
							}
							returnedResults.put(undeclaredName, updateCount);
							updateIndex++;
						}
					}
				}
				moreResults = cs.getMoreResults();
				updateCount = cs.getUpdateCount();
				if (logger.isDebugEnabled()) {
					logger.debug("CallableStatement.getUpdateCount() returned " + updateCount);
				}
			}
			while (moreResults || updateCount != -1);
		}
		return returnedResults;
	}

	/**
	 * Extract output parameters from the completed stored procedure.
	 * @param cs JDBC wrapper for the stored procedure
	 * @param parameters parameter list for the stored procedure
	 * @return Map that contains returned results
	 */
	protected Map<String, Object> extractOutputParameters(CallableStatement cs, List<SqlParameter> parameters)
			throws SQLException {

		Map<String, Object> returnedResults = new HashMap<>();
		int sqlColIndex = 1;
		for (SqlParameter param : parameters) {
			if (param instanceof SqlOutParameter) {
				SqlOutParameter outParam = (SqlOutParameter) param;
				Assert.state(outParam.getName() != null, "Anonymous parameters not allowed");
				SqlReturnType returnType = outParam.getSqlReturnType();
				if (returnType != null) {
					Object out = returnType.getTypeValue(cs, sqlColIndex, outParam.getSqlType(), outParam.getTypeName());
					returnedResults.put(outParam.getName(), out);
				}
				else {
					Object out = cs.getObject(sqlColIndex);
					if (out instanceof ResultSet) {
						if (outParam.isResultSetSupported()) {
							returnedResults.putAll(processResultSet((ResultSet) out, outParam));
						}
						else {
							String rsName = outParam.getName();
							SqlReturnResultSet rsParam = new SqlReturnResultSet(rsName, getColumnMapRowMapper());
							returnedResults.putAll(processResultSet((ResultSet) out, rsParam));
							if (logger.isDebugEnabled()) {
								logger.debug("Added default SqlReturnResultSet parameter named '" + rsName + "'");
							}
						}
					}
					else {
						returnedResults.put(outParam.getName(), out);
					}
				}
			}
			if (!(param.isResultsParameter())) {
				sqlColIndex++;
			}
		}
		return returnedResults;
	}

	/**
	 * Process the given ResultSet from a stored procedure.
	 * @param rs the ResultSet to process
	 * @param param the corresponding stored procedure parameter
	 * @return Map that contains returned results
	 */
	protected Map<String, Object> processResultSet(
			@Nullable ResultSet rs, ResultSetSupportingSqlParameter param) throws SQLException {

		if (rs == null) {
			return Collections.emptyMap();
		}

		Map<String, Object> returnedResults = new HashMap<>();
		try {
			if (param.getRowMapper() != null) {
				RowMapper<?> rowMapper = param.getRowMapper();
				Object result = (new RowMapperResultSetExtractor<>(rowMapper)).extractData(rs);
				returnedResults.put(param.getName(), result);
			}
			else if (param.getRowCallbackHandler() != null) {
				RowCallbackHandler rch = param.getRowCallbackHandler();
				(new RowCallbackHandlerResultSetExtractor(rch)).extractData(rs);
				returnedResults.put(param.getName(), "ResultSet returned from stored procedure was processed");
			}
			else if (param.getResultSetExtractor() != null) {
				Object result = param.getResultSetExtractor().extractData(rs);
				returnedResults.put(param.getName(), result);
			}
		}
		finally {
			JdbcUtils.closeResultSet(rs);
		}
		return returnedResults;
	}


	//-------------------------------------------------------------------------
	// Implementation hooks and helper methods
	//-------------------------------------------------------------------------

	/**
	 * Create a new RowMapper for reading columns as key-value pairs.
	 * @return the RowMapper to use
	 * @see ColumnMapRowMapper
	 */
	protected RowMapper<Map<String, Object>> getColumnMapRowMapper() {
		return new ColumnMapRowMapper();
	}

	/**
	 * Create a new RowMapper for reading result objects from a single column.
	 * @param requiredType the type that each result object is expected to match
	 * @return the RowMapper to use
	 * @see SingleColumnRowMapper
	 */
	protected <T> RowMapper<T> getSingleColumnRowMapper(Class<T> requiredType) {
		return new SingleColumnRowMapper<>(requiredType);
	}

	/**
	 * Create a Map instance to be used as the results map.
	 * <p>If {@link #resultsMapCaseInsensitive} has been set to true,
	 * a {@link LinkedCaseInsensitiveMap} will be created; otherwise, a
	 * {@link LinkedHashMap} will be created.
	 * @return the results Map instance
	 * @see #setResultsMapCaseInsensitive
	 * @see #isResultsMapCaseInsensitive
	 */
	protected Map<String, Object> createResultsMap() {
		if (isResultsMapCaseInsensitive()) {
			return new LinkedCaseInsensitiveMap<>();
		}
		else {
			return new LinkedHashMap<>();
		}
	}

	/**
	 * Prepare the given JDBC Statement (or PreparedStatement or CallableStatement),
	 * applying statement settings such as fetch size, max rows, and query timeout.
	 * @param stmt the JDBC Statement to prepare
	 * @throws SQLException if thrown by JDBC API
	 * @see #setFetchSize
	 * @see #setMaxRows
	 * @see #setQueryTimeout
	 * @see org.springframework.jdbc.datasource.DataSourceUtils#applyTransactionTimeout
	 */
//		setFetchSize 最主要是为了减少网络交互次数设计的。访问ResultSet时，如果它每次只从服务器上读取一行数据，
//	则会产生大量的开销。setFetchSize的 意思是当调用 rs.next 时,ResultSet 会一次性从服务器上取得多少行数据回来，这
//	样在下次 rs.next 时，它可以直接从内存中获取数据而不需要网络交互，提高了效率。这个设置可能会被某些 JDBC 驱动忽略，
//	而且设置过大也会造成内存的上升。
//		setMaxRows 将此 Statement 对象生成的所有 ResultSet 对象可以包含的最大行数限制设置为给定数。
	protected void applyStatementSettings(Statement stmt) throws SQLException {
		int fetchSize = getFetchSize();
		if (fetchSize != -1) {
			stmt.setFetchSize(fetchSize);
		}
		int maxRows = getMaxRows();
		if (maxRows != -1) {
			stmt.setMaxRows(maxRows);
		}
		DataSourceUtils.applyTimeout(stmt, getDataSource(), getQueryTimeout());
	}

	/**
	 * Create a new arg-based PreparedStatementSetter using the args passed in.
	 * <p>By default, we'll create an {@link ArgumentPreparedStatementSetter}.
	 * This method allows for the creation to be overridden by subclasses.
	 * @param args object array with arguments
	 * @return the new PreparedStatementSetter to use
	 */
	protected PreparedStatementSetter newArgPreparedStatementSetter(@Nullable Object[] args) {
		return new ArgumentPreparedStatementSetter(args);
	}

	/**
	 * Create a new arg-type-based PreparedStatementSetter using the args and types passed in.
	 * <p>By default, we'll create an {@link ArgumentTypePreparedStatementSetter}.
	 * This method allows for the creation to be overridden by subclasses.
	 * @param args object array with arguments
	 * @param argTypes int array of SQLTypes for the associated arguments
	 * @return the new PreparedStatementSetter to use
	 */

	protected PreparedStatementSetter newArgTypePreparedStatementSetter(Object[] args, int[] argTypes) {
		return new ArgumentTypePreparedStatementSetter(args, argTypes);
	}

	/**
	 * Throw an SQLWarningException if we're not ignoring warnings,
	 * else log the warnings (at debug level).
	 * @param stmt the current JDBC statement
	 * @throws SQLWarningException if not ignoring warnings
	 * @see org.springframework.jdbc.SQLWarningException
	 */
//		这里用到了一个类 SQLWarning, SQLWarning 提供关于数据库访何警告信息的异常。这些警告直接链接到导致报告警告的方法所在的对象。
//	警吿可以从 Connection、Statement 和 ResultSet 对象中获得。试图在已经关闭的连接上获取警告将导致抛出异常。
//		类似地，试图在已经关闭的语句上或已经关闭的结果集上获取警告也将导致抛出异常。注意，关闭语句时还会关闭它可能生成的结果集。
//		很多人不是很理解什么情况下会产生警告而不是异常，在这里给读者提示个最常见的警吿 DataTruncation: DataTruncation 直接继承SQLWarning,
//	由于某种原因意外地截断数据值时会以 DataTruncation 警告形式报吿异常。
//		对于警告的处理方式并不是直接抛出异常，出现警告很可能会出现数据错误，但是，并不一定会影响程序执行，所以用户可以自己设置处理警告的方式，如默认的是忽略警吿，
//	当出现警吿时只打印警吿日志，而另一种方式只直接抛出异常。
	protected void handleWarnings(Statement stmt) throws SQLException {
		/// 当设置为忽略警告时只尝试打印日志
		if (isIgnoreWarnings()) {
			if (logger.isDebugEnabled()) {
				/// 如果日志开启的情况下打印日志
				SQLWarning warningToLog = stmt.getWarnings();
				while (warningToLog != null) {
					logger.debug("SQLWarning ignored: SQL state '" + warningToLog.getSQLState() + "', error code '" +
							warningToLog.getErrorCode() + "', message [" + warningToLog.getMessage() + "]");
					warningToLog = warningToLog.getNextWarning();
				}
			}
		}
		else {
			handleWarnings(stmt.getWarnings());
		}
	}

	/**
	 * Throw an SQLWarningException if encountering an actual warning.
	 * @param warning the warnings object from the current statement.
	 * May be {@code null}, in which case this method does nothing.
	 * @throws SQLWarningException in case of an actual warning to be raised
	 */
	protected void handleWarnings(@Nullable SQLWarning warning) throws SQLWarningException {
		if (warning != null) {
			throw new SQLWarningException("Warning not ignored", warning);
		}
	}

	/**
	 * Translate the given {@link SQLException} into a generic {@link DataAccessException}.
	 * @param task readable text describing the task being attempted
	 * @param sql SQL query or update that caused the problem (may be {@code null})
	 * @param ex the offending {@code SQLException}
	 * @return a DataAccessException wrapping the {@code SQLException} (never {@code null})
	 * @since 5.0
	 * @see #getExceptionTranslator()
	 */
	protected DataAccessException translateException(String task, @Nullable String sql, SQLException ex) {
		DataAccessException dae = getExceptionTranslator().translate(task, sql, ex);
		return (dae != null ? dae : new UncategorizedSQLException(task, sql, ex));
	}


	/**
	 * Determine SQL from potential provider object.
	 * @param sqlProvider object that's potentially a SqlProvider
	 * @return the SQL string, or {@code null}
	 * @see SqlProvider
	 */
	@Nullable
	private static String getSql(Object sqlProvider) {
		if (sqlProvider instanceof SqlProvider) {
			return ((SqlProvider) sqlProvider).getSql();
		}
		else {
			return null;
		}
	}

	private static <T> T result(@Nullable T result) {
		Assert.state(result != null, "No result");
		return result;
	}

	private static int updateCount(@Nullable Integer result) {
		Assert.state(result != null, "No update count");
		return result;
	}


	/**
	 * Invocation handler that suppresses close calls on JDBC Connections.
	 * Also prepares returned Statement (Prepared/CallbackStatement) objects.
	 * @see java.sql.Connection#close()
	 */
	private class CloseSuppressingInvocationHandler implements InvocationHandler {

		private final Connection target;

		public CloseSuppressingInvocationHandler(Connection target) {
			this.target = target;
		}

		@Override
		@Nullable
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// Invocation on ConnectionProxy interface coming in...

			if (method.getName().equals("equals")) {
				// Only consider equal when proxies are identical.
				return (proxy == args[0]);
			}
			else if (method.getName().equals("hashCode")) {
				// Use hashCode of PersistenceManager proxy.
				return System.identityHashCode(proxy);
			}
			else if (method.getName().equals("unwrap")) {
				if (((Class<?>) args[0]).isInstance(proxy)) {
					return proxy;
				}
			}
			else if (method.getName().equals("isWrapperFor")) {
				if (((Class<?>) args[0]).isInstance(proxy)) {
					return true;
				}
			}
			else if (method.getName().equals("close")) {
				// Handle close method: suppress, not valid.
				return null;
			}
			else if (method.getName().equals("isClosed")) {
				return false;
			}
			else if (method.getName().equals("getTargetConnection")) {
				// Handle getTargetConnection method: return underlying Connection.
				return this.target;
			}

			// Invoke method on target Connection.
			try {
				Object retVal = method.invoke(this.target, args);

				// If return value is a JDBC Statement, apply statement settings
				// (fetch size, max rows, transaction timeout).
				if (retVal instanceof Statement) {
					applyStatementSettings(((Statement) retVal));
				}

				return retVal;
			}
			catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}


	/**
	 * Simple adapter for PreparedStatementCreator, allowing to use a plain SQL statement.
	 */
	private static class SimplePreparedStatementCreator implements PreparedStatementCreator, SqlProvider {

		private final String sql;

		public SimplePreparedStatementCreator(String sql) {
			Assert.notNull(sql, "SQL must not be null");
			this.sql = sql;
		}

		@Override
		public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
			return con.prepareStatement(this.sql);
		}

		@Override
		public String getSql() {
			return this.sql;
		}
	}


	/**
	 * Simple adapter for CallableStatementCreator, allowing to use a plain SQL statement.
	 */
	private static class SimpleCallableStatementCreator implements CallableStatementCreator, SqlProvider {

		private final String callString;

		public SimpleCallableStatementCreator(String callString) {
			Assert.notNull(callString, "Call string must not be null");
			this.callString = callString;
		}

		@Override
		public CallableStatement createCallableStatement(Connection con) throws SQLException {
			return con.prepareCall(this.callString);
		}

		@Override
		public String getSql() {
			return this.callString;
		}
	}


	/**
	 * Adapter to enable use of a RowCallbackHandler inside a ResultSetExtractor.
	 * <p>Uses a regular ResultSet, so we have to be careful when using it:
	 * We don't use it for navigating since this could lead to unpredictable consequences.
	 */
	private static class RowCallbackHandlerResultSetExtractor implements ResultSetExtractor<Object> {

		private final RowCallbackHandler rch;

		public RowCallbackHandlerResultSetExtractor(RowCallbackHandler rch) {
			this.rch = rch;
		}

		@Override
		@Nullable
		public Object extractData(ResultSet rs) throws SQLException {
			while (rs.next()) {
				this.rch.processRow(rs);
			}
			return null;
		}
	}

}
