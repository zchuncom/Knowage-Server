package it.eng.spagobi.tools.hierarchiesmanagement.service.rest;

import it.eng.spagobi.commons.dao.DAOFactory;
import it.eng.spagobi.container.ObjectUtils;
import it.eng.spagobi.tools.dataset.bo.AbstractJDBCDataset;
import it.eng.spagobi.tools.dataset.common.datastore.IDataStore;
import it.eng.spagobi.tools.dataset.common.datastore.IField;
import it.eng.spagobi.tools.dataset.common.datastore.IRecord;
import it.eng.spagobi.tools.dataset.common.metadata.IMetaData;
import it.eng.spagobi.tools.datasource.bo.IDataSource;
import it.eng.spagobi.tools.datasource.dao.IDataSourceDAO;
import it.eng.spagobi.tools.hierarchiesmanagement.Hierarchies;
import it.eng.spagobi.tools.hierarchiesmanagement.HierarchiesSingleton;
import it.eng.spagobi.tools.hierarchiesmanagement.HierarchyTreeNode;
import it.eng.spagobi.tools.hierarchiesmanagement.HierarchyTreeNodeData;
import it.eng.spagobi.tools.hierarchiesmanagement.TreeString;
import it.eng.spagobi.tools.hierarchiesmanagement.metadata.Field;
import it.eng.spagobi.tools.hierarchiesmanagement.metadata.Hierarchy;
import it.eng.spagobi.tools.hierarchiesmanagement.utils.HierarchyConstants;
import it.eng.spagobi.tools.hierarchiesmanagement.utils.HierarchyUtils;
import it.eng.spagobi.utilities.assertion.Assert;
import it.eng.spagobi.utilities.exceptions.SpagoBIServiceException;
import it.eng.spagobi.utilities.rest.RestUtilities;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/*
 * This class contains all REST services used by all hierarchy types (master and technical)
 */

@Path("/hierarchies")
public class HierarchyService {

	static private Logger logger = Logger.getLogger(HierarchyService.class);

	// get automatic hierarchy structure for tree visualization
	@GET
	@Path("/getHierarchyTree")
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	public String getHierarchyTree(@QueryParam("dimension") String dimension, @QueryParam("filterType") String hierarchyType,
			@QueryParam("filterHierarchy") String hierarchyName, @QueryParam("validityDate") String hierarchyDate,
			@QueryParam("filterDimension") String filterDimension, @QueryParam("optionDate") String optionDate,
			@QueryParam("optionHierarchy") String optionHierarchy, @QueryParam("optionHierType") String optionHierType) {
		logger.debug("START");

		HierarchyTreeNode hierarchyTree;
		JSONObject treeJSONObject;
		try {
			// Check input parameters
			Assert.assertNotNull(dimension, "Request parameter dimension is null");
			Assert.assertNotNull(hierarchyType, "Request parameter hierarchyType is null");
			Assert.assertNotNull(hierarchyName, "Request parameter hierarchyName is null");
			Assert.assertNotNull(hierarchyDate, "Request parameter hierarchyDate is null");

			IDataSource dataSource = null;
			// 1 - get datasource label name
			try {
				dataSource = HierarchyUtils.getDataSource(dimension);
			} catch (SpagoBIServiceException se) {
				throw se;
			}

			// 2 - execute query to get hierarchies leafs
			IMetaData metadata = null;
			String queryText = this.createQueryHierarchy(dataSource, dimension, hierarchyType, hierarchyName, hierarchyDate, filterDimension, optionDate,
					optionHierarchy, optionHierType);
			IDataStore dataStore = dataSource.executeStatement(queryText, 0, 0);

			// 4 - Create ADT for Tree from datastore
			hierarchyTree = createHierarchyTreeStructure(dataStore, dimension, metadata);
			treeJSONObject = convertHierarchyTreeAsJSON(hierarchyTree, hierarchyName, dimension);

			if (treeJSONObject == null)
				return null;

		} catch (Throwable t) {
			logger.error("An unexpected error occured while retriving hierarchy structure");
			throw new SpagoBIServiceException("An unexpected error occured while retriving hierarchy structure", t);
		}
		logger.debug("END");
		return treeJSONObject.toString();
	}

	@GET
	@Path("/hierarchyMetadata")
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	public String getHierarchyFields(@QueryParam("dimension") String dimensionName) {

		logger.debug("START");

		JSONObject result = new JSONObject();

		try {

			result = createHierarchyJSON(dimensionName, false);

		} catch (Throwable t) {
			logger.error("An unexpected error occured while creating dimensions json");
			throw new SpagoBIServiceException("An unexpected error occured while creating dimensions json", t);
		}

		logger.debug("END");
		return result.toString();
	}

	@GET
	@Path("/nodeMetadata")
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	public String getHierarchyNodeFields(@QueryParam("dimension") String dimensionName, @QueryParam("excludeLeaf") boolean excludeLeaf) {

		logger.debug("START");

		JSONObject result = new JSONObject();

		try {

			result = createHierarchyJSON(dimensionName, excludeLeaf);

		} catch (Throwable t) {
			logger.error("An unexpected error occured while creating dimensions json");
			throw new SpagoBIServiceException("An unexpected error occured while creating dimensions json", t);
		}

		logger.debug("END");
		return result.toString();
	}

	@POST
	@Path("/saveHierarchy")
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	public String saveHierarchy(@Context HttpServletRequest req) {
		Connection connection = null;
		try {

			JSONObject requestVal = RestUtilities.readBodyAsJSONObject(req);

			HashMap<String, Object> paramsMap = new HashMap<String, Object>();

			String validityDate = (!requestVal.isNull("dateValidity")) ? requestVal.getString("dateValidity") : null;
			paramsMap.put("validityDate", validityDate);
			boolean doBackup = (!requestVal.isNull("doBackup")) ? requestVal.getBoolean("doBackup") : new Boolean("false");
			paramsMap.put("doBackup", doBackup);
			boolean isInsert = Boolean.valueOf(req.getParameter("isInsert"));
			paramsMap.put("isInsert", isInsert);
			String dimension = requestVal.getString("dimension");
			paramsMap.put("dimension", dimension);
			String hierSourceCode = (!requestVal.isNull("hierSourceCode")) ? requestVal.getString("hierSourceCode") : null;
			paramsMap.put("hierSourceName", hierSourceCode);
			String hierSourceName = (!requestVal.isNull("hierSourceName")) ? requestVal.getString("hierSourceName") : null;
			paramsMap.put("hierSourceName", hierSourceName);
			String hierSourceType = (!requestVal.isNull("hierSourceType")) ? requestVal.getString("hierSourceType") : null;
			paramsMap.put("hierSourceType", hierSourceType);

			// ----------- ONLY FOR TEST -------------------
			hierSourceCode = "BPC_PATR"; // aggiungere nella request come parametro
			hierSourceName = "BPC_PATR"; // aggiungere nella request come parametro
			hierSourceType = "MASTER"; // aggiungere nella request come parametro
			paramsMap.put("hierSourceCode", hierSourceCode);
			paramsMap.put("hierSourceName", hierSourceName);
			paramsMap.put("hierSourceType", hierSourceType);
			// ----------- END ONLY FOR TEST -------------------

			String root = requestVal.getString("root"); // tree
			JSONObject rootJSONObject = ObjectUtils.toJSONObject(root);
			String hierTargetCode = rootJSONObject.getString(HierarchyConstants.HIER_CD);
			paramsMap.put("hierTargetCode", hierTargetCode);
			String hierTargetName = rootJSONObject.getString(HierarchyConstants.HIER_NM);
			paramsMap.put("hierTargetName", hierTargetName);
			String hierTargetType = rootJSONObject.getString(HierarchyConstants.HIER_TP);
			paramsMap.put("hierTargetType", hierTargetType);

			// 1 - get informations for persistence (ie. hierarchy table postfix..)
			Hierarchies hierarchies = HierarchiesSingleton.getInstance();
			String hierarchyTable = hierarchies.getHierarchyTableName(dimension);
			paramsMap.put("hierarchyTable", hierarchyTable);
			String hierarchyPrefix = hierarchies.getPrefix(dimension);
			paramsMap.put("hierarchyPrefix", hierarchyPrefix);
			String hierarchyFK = hierarchies.getHierarchyTableForeignKeyName(dimension);
			paramsMap.put("hierarchyFK", hierarchyFK);
			Hierarchy hierarchyFields = hierarchies.getHierarchy(dimension);
			HashMap<String, Object> hierConfig = hierarchies.getConfig(dimension);

			// 2 - Definition of the context (ex. manage propagations ONLY when the sourceHierType is MASTER and the targtHierType is TECHNICAL)
			boolean doPropagation = false;
			if (hierSourceType != null && hierSourceType.equals(HierarchyConstants.HIER_TP_MASTER) && hierTargetType != null
					&& hierTargetType.equals(HierarchyConstants.HIER_TP_TECHNICAL)) {
				// doPropagation = true; // ONLY FOR COMMIT: disable the relations management
				paramsMap.put("doPropagation", doPropagation);
			}

			// 3 - get all paths from the input json tree
			Collection<List<HierarchyTreeNodeData>> paths = findRootToLeavesPaths(rootJSONObject, dimension);

			// 4 - get datasource label name
			String dataSourceName = hierarchies.getDataSourceOfDimension(dimension);
			IDataSourceDAO dataSourceDAO = DAOFactory.getDataSourceDAO();
			IDataSource dataSource = dataSourceDAO.loadDataSourceByLabel(dataSourceName);
			if (dataSource == null) {
				throw new SpagoBIServiceException("An unexpected error occured while saving custom hierarchy", "No datasource found for saving hierarchy");
			}

			// get one ONLY connection for all statements (transactional logic)
			connection = dataSource.getConnection();
			connection.setAutoCommit(false);

			if (!isInsert && doBackup) {
				updateHierarchyForBackup(dataSource, connection, paramsMap);
			} else if (!isInsert && !doBackup) {
				deleteHierarchy(dimension, hierSourceName, dataSource, connection);
			}

			for (List<HierarchyTreeNodeData> path : paths) {
				persistHierarchyPath(connection, dataSource, paramsMap, path, hierarchyFields, hierConfig);
			}

			// OK - commit ALL changes!
			connection.commit();
			return "{\"response\":\"ok\"}";
		} catch (Throwable t) {
			logger.error("An unexpected error occured while saving custom hierarchy structure");
			try {
				if (connection != null && !connection.isClosed()) {
					connection.rollback();
				}
			} catch (SQLException sqle) {
				throw new SpagoBIServiceException("An unexpected error occured while saving custom hierarchy structure", sqle);
			}
			throw new SpagoBIServiceException("An unexpected error occured while saving custom hierarchy structure", t);
		} finally {
			try {
				if (connection != null && !connection.isClosed()) {
					connection.close();
				}
			} catch (SQLException sqle) {
				throw new SpagoBIServiceException("An unexpected error occured while saving custom hierarchy structure", sqle);
			}
		}
	}

	@POST
	@Path("/deleteHierarchy")
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	public String deleteHierarchy(@Context HttpServletRequest req) throws SQLException {
		// delete hierarchy
		Connection connection = null;
		try {

			JSONObject requestVal = RestUtilities.readBodyAsJSONObject(req);

			String dimension = requestVal.getString("dimension");
			String hierarchyName = requestVal.getString("name");

			// 1 - get datasource label name
			Hierarchies hierarchies = HierarchiesSingleton.getInstance();
			String dataSourceName = hierarchies.getDataSourceOfDimension(dimension);
			IDataSourceDAO dataSourceDAO = DAOFactory.getDataSourceDAO();
			IDataSource dataSource = dataSourceDAO.loadDataSourceByLabel(dataSourceName);

			// 2 - Execute DELETE
			connection = dataSource.getConnection();
			deleteHierarchy(dimension, hierarchyName, dataSource, connection);

		} catch (Throwable t) {
			connection.rollback();
			logger.error("An unexpected error occured while deleting custom hierarchy");
			throw new SpagoBIServiceException("An unexpected error occured while deleting custom hierarchy", t);
		} finally {
			if (connection != null && !connection.isClosed())
				connection.close();
		}

		return "{\"response\":\"ok\"}";

	}

	@GET
	@Path("/getRelationsMasterTechnical")
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	// public String getRelationsMasterTechnical(@Context HttpServletRequest req) throws SQLException {
	public String getRelationsMasterTechnical(@QueryParam("dimension") String dimension, @QueryParam("hierSourceCode") String hierSourceCode,
			@QueryParam("hierSourceName") String hierSourceName, @QueryParam("nodeSourceCode") String nodeSourceCode) throws SQLException {
		// get relations between master and technical nodes
		Connection connection = null;
		JSONObject result = new JSONObject();
		try {

			// JSONObject requestVal = RestUtilities.readBodyAsJSONObject(req);

			HashMap<String, Object> paramsMap = new HashMap<String, Object>();
			// String dimension = requestVal.getString("dimension");
			paramsMap.put(HierarchyConstants.DIMENSION, dimension);
			// String hierSourceCode = (!requestVal.isNull("hierSourceCode")) ? requestVal.getString("hierSourceCode") : null;
			paramsMap.put(HierarchyConstants.HIER_CD_M, hierSourceCode);
			// String hierSourceName = (!requestVal.isNull("hierSourceName")) ? requestVal.getString("hierSourceName") : null;
			paramsMap.put(HierarchyConstants.HIER_NM_M, hierSourceName);
			// String nodeSourceCode = (!requestVal.isNull("nodeSourceCode")) ? requestVal.getString("nodeSourceCode") : null;
			paramsMap.put(HierarchyConstants.NODE_CD_M, nodeSourceCode);

			// ----------- ONLY FOR TEST -------------------
			// hierSourceCode = "BPC_PATR"; // aggiungere nella request come parametro
			// hierSourceName = "BPC_PATR"; // aggiungere nella request come parametro
			// nodeSourceCode = "PAS_TP_PC_AP_PRE"; // aggiungere nella request come parametro
			// paramsMap.put("hierSourceCode", hierSourceCode);
			// paramsMap.put("hierSourceName", hierSourceName);
			// paramsMap.put("nodeSourceCode", nodeSourceCode);
			// ----------- END ONLY FOR TEST -------------------

			// 1 - get datasource label name
			Hierarchies hierarchies = HierarchiesSingleton.getInstance();
			String dataSourceName = hierarchies.getDataSourceOfDimension(dimension);
			IDataSourceDAO dataSourceDAO = DAOFactory.getDataSourceDAO();
			IDataSource dataSource = dataSourceDAO.loadDataSourceByLabel(dataSourceName);

			// 2 - Execute query
			connection = dataSource.getConnection();

			String selectQuery = createQueryRelationsHierarchy(dataSource, paramsMap);
			IDataStore dataStore = dataSource.executeStatement(selectQuery, 0, 0);

			// Create JSON for relational data from datastore
			JSONArray dataArray = HierarchyUtils.createRootData(dataStore);

			logger.debug("Root array is [" + dataArray.toString() + "]");
			result.put(HierarchyConstants.ROOT, dataArray);

		} catch (Throwable t) {
			connection.rollback();
			logger.error("An unexpected error occured while deleting custom hierarchy");
			throw new SpagoBIServiceException("An unexpected error occured while deleting custom hierarchy", t);
		} finally {
			if (connection != null && !connection.isClosed())
				connection.close();
		}
		logger.debug("JSON for relational data is [" + result.toString() + "]");
		logger.debug("END");

		return result.toString();
	}

	// public static JSONArray createRelationalData(IDataStore dataStore) throws JSONException {
	//
	// logger.debug("START");
	//
	// JSONArray rootArray = new JSONArray();
	//
	// IMetaData columnsMetaData = dataStore.getMetaData();
	//
	// Iterator iterator = dataStore.iterator();
	//
	// while (iterator.hasNext()) {
	//
	// IRecord record = (IRecord) iterator.next();
	// List<IField> recordFields = record.getFields();
	//
	// JSONObject tmpJSON = new JSONObject();
	//
	// for (int i = 0; i < recordFields.size(); i++) {
	//
	// IField tmpField = recordFields.get(i);
	//
	// String tmpKey = columnsMetaData.getFieldName(i);
	// tmpJSON.put(tmpKey, tmpField.getValue());
	// }
	//
	// rootArray.put(tmpJSON);
	// }
	//
	// logger.debug("END");
	// return rootArray;
	// }

	private boolean deleteHierarchy(String dimension, String hierarchyName, IDataSource dataSource, Connection connection) throws SQLException {
		// delete hierarchy

		logger.debug("START");

		try {

			// String dimension = requestVal.getString("dimension");
			// String hierarchyName = requestVal.getString("name");

			logger.debug("Preparing delete statement. Name of the hierarchy is [" + hierarchyName + "]");

			// 1 - get hierarchy table postfix(ex: _CDC)
			Hierarchies hierarchies = HierarchiesSingleton.getInstance();

			// 2 - create query text
			String hierarchyNameCol = AbstractJDBCDataset.encapsulateColumnName("HIER_NM", dataSource);
			String tableName = hierarchies.getHierarchyTableName(dimension);
			String queryText = "DELETE FROM " + tableName + " WHERE " + hierarchyNameCol + "=\"" + hierarchyName + "\" ";

			logger.debug("The delete query is [" + queryText + "]");

			// 3 - Execute DELETE statement
			Statement statement = connection.createStatement();
			statement.executeUpdate(queryText);
			statement.close();

			logger.debug("Delete query successfully executed");
			logger.debug("END");

		} catch (Throwable t) {
			logger.error("An unexpected error occured while deleting custom hierarchy");
			throw new SpagoBIServiceException("An unexpected error occured while deleting custom hierarchy", t);
		}

		return true;

	}

	/**
	 * This method manages the creation of the JSON for hierarchies fields
	 *
	 * @param dimensionName
	 *            the name of the dimension
	 * @param excludeLeaf
	 *            exclusion for fields in leaf section
	 * @return the JSON with fields in hierarchy section
	 * @throws JSONException
	 */
	private JSONObject createHierarchyJSON(String dimensionName, boolean excludeLeaf) throws JSONException {

		logger.debug("START");

		JSONObject result = new JSONObject();

		Hierarchies hierarchies = HierarchiesSingleton.getInstance();
		Assert.assertNotNull(hierarchies, "Impossible to find valid hierarchies config");

		Hierarchy hierarchy = hierarchies.getHierarchy(dimensionName);
		Assert.assertNotNull(hierarchy, "Impossible to find a hierarchy for the dimension called [" + dimensionName + "]");

		JSONObject configs = HierarchyUtils.createJSONArrayFromHashMap(hierarchies.getConfig(dimensionName), null);
		result.put(HierarchyConstants.CONFIGS, configs);

		List<Field> generalMetadataFields = new ArrayList<Field>(hierarchy.getMetadataGeneralFields());
		JSONArray generalFieldsJSONArray = HierarchyUtils.createJSONArrayFromFieldsList(generalMetadataFields, true);
		result.put(HierarchyConstants.GENERAL_FIELDS, generalFieldsJSONArray);

		List<Field> nodeMetadataFields = new ArrayList<Field>(hierarchy.getMetadataNodeFields());

		JSONArray nodeFieldsJSONArray = HierarchyUtils.createJSONArrayFromFieldsList(nodeMetadataFields, true);
		result.put(HierarchyConstants.NODE_FIELDS, nodeFieldsJSONArray);

		if (!excludeLeaf) { // add leaf fields
			List<Field> leafMetadataFields = new ArrayList<Field>(hierarchy.getMetadataLeafFields());

			JSONArray leafFieldsJSONArray = HierarchyUtils.createJSONArrayFromFieldsList(leafMetadataFields, true);
			result.put(HierarchyConstants.LEAF_FIELDS, leafFieldsJSONArray);
		}

		logger.debug("END");
		return result;

	}

	/**
	 * Create query for extracting automatic hierarchy rows
	 */
	private String createQueryHierarchy(IDataSource dataSource, String dimension, String hierarchyType, String hierarchyName, String hierarchyDate,
			String filterDimension, String optionDate, String optionHierarchy, String optionHierType) {

		Hierarchies hierarchies = HierarchiesSingleton.getInstance();

		// 1 -get hierarchy informations
		String hierarchyTable = hierarchies.getHierarchyTableName(dimension);
		String dimensionName = (hierarchies.getDimension(dimension).getName());
		String prefix = hierarchies.getPrefix(dimension);
		Hierarchy hierarchyFields = hierarchies.getHierarchy(dimension);
		Assert.assertNotNull(hierarchyFields, "Impossible to find a hierarchy configurations for the dimension called [" + dimension + "]");
		HashMap hierConfig = hierarchies.getConfig(dimension);

		List<Field> generalMetadataFields = new ArrayList<Field>(hierarchyFields.getMetadataGeneralFields());
		List<Field> nodeMetadataFields = new ArrayList<Field>(hierarchyFields.getMetadataNodeFields());
		List<Field> leafMetadataFields = new ArrayList<Field>(hierarchyFields.getMetadataLeafFields());

		// 2 - get total columns number
		int totalColumns = 0;
		int totalLevels = Integer.parseInt((String) hierConfig.get(HierarchyConstants.NUM_LEVELS));
		int totalGeneralFields = generalMetadataFields.size();
		int totalLeafFields = leafMetadataFields.size();
		int totalNodeFields = getTotalNodeFieldsNumber(totalLevels, nodeMetadataFields);

		totalColumns = totalGeneralFields + totalLeafFields + totalNodeFields;

		// 3 - define select command
		StringBuffer selectClauseBuffer = new StringBuffer(" ");
		StringBuffer orderClauseBuffer = new StringBuffer(" ");
		// general fields:
		for (int i = 0, l = generalMetadataFields.size(); i < l; i++) {
			Field f = generalMetadataFields.get(i);
			String sep = ", ";
			String column = AbstractJDBCDataset.encapsulateColumnName(f.getId(), dataSource);
			selectClauseBuffer.append(column + sep);
		}
		// node fields:
		for (int i = 0, l = nodeMetadataFields.size(); i < l; i++) {
			Field f = nodeMetadataFields.get(i);
			String sep = ", ";
			String column = "";
			if (f.isSingleValue()) {
				column = AbstractJDBCDataset.encapsulateColumnName(f.getId(), dataSource);
				selectClauseBuffer.append(column + sep);
				// // add first node column as order field:
				// if (i == 0)
				// orderClauseBuffer.append(column);
			} else {
				for (int i2 = 1, l2 = totalLevels; i2 <= l2; i2++) {
					sep = ",";
					column = AbstractJDBCDataset.encapsulateColumnName(f.getId() + i2, dataSource);
					selectClauseBuffer.append(column + sep);
					// // add first node column as order field:
					// if (i == 0 && i2 == 1)
					// orderClauseBuffer.append(column);
				}
			}
		}

		// order clause
		for (int o = 1, l2 = totalLevels; o <= l2; o++) {
			String sep = (o == totalLevels) ? "" : ",";
			String column = AbstractJDBCDataset.encapsulateColumnName(prefix + HierarchyConstants.SUFFIX_CD_LEV + o, dataSource);
			orderClauseBuffer.append(column + sep);
		}

		// leaf fields:
		for (int i = 0, l = leafMetadataFields.size(); i < l; i++) {
			Field f = leafMetadataFields.get(i);
			String sep = (i == l - 1) ? " " : ",";
			String column = AbstractJDBCDataset.encapsulateColumnName(f.getId(), dataSource);
			selectClauseBuffer.append(column + sep);
		}
		String selectClause = selectClauseBuffer.toString();

		// where
		String hierNameColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.HIER_NM, dataSource);
		String hierTypeColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.HIER_TP, dataSource);
		String hierDateBeginColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.BEGIN_DT, dataSource);
		String hierDateEndColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.END_DT, dataSource);
		String vDateConverted = HierarchyUtils.getConvertedDate(hierarchyDate, dataSource);

		String vDateWhereClause = vDateConverted + " >= " + hierDateBeginColumn + " AND " + vDateConverted + " <= " + hierDateEndColumn;

		StringBuffer query = new StringBuffer("SELECT " + selectClause + " FROM " + hierarchyTable + " WHERE " + hierNameColumn + " = \"" + hierarchyName
				+ "\" AND " + hierTypeColumn + " = \"" + hierarchyType + "\" AND " + vDateWhereClause);

		if (filterDimension != null) {
			logger.debug("Filter dimension is [" + filterDimension + "]");

			String dimFilterField = AbstractJDBCDataset.encapsulateColumnName(prefix + "_CD_LEAF", dataSource);
			String selectFilterField = AbstractJDBCDataset.encapsulateColumnName(prefix + "_CD", dataSource);

			query.append(" AND " + dimFilterField + " NOT IN (SELECT " + selectFilterField + "FROM " + dimensionName);
			query.append(" WHERE " + vDateConverted + " >= " + hierDateBeginColumn + " AND " + vDateConverted + " <= " + hierDateEndColumn + ")");
		}

		if (optionDate != null) {
			logger.debug("Filter date is [" + optionDate + "]");

			query.append(HierarchyUtils.createDateAfterCondition(dataSource, optionDate, hierDateBeginColumn));
		}

		if (optionHierarchy != null) {
			logger.debug("Filter Hierarchy is [" + optionHierarchy + "]");

			String dimFilterField = AbstractJDBCDataset.encapsulateColumnName(prefix + "_CD_LEAF", dataSource);

			query.append(HierarchyUtils.createNotInHierarchyCondition(dataSource, hierarchyTable, hierNameColumn, optionHierarchy, hierTypeColumn,
					optionHierType, dimFilterField, dimFilterField, vDateWhereClause));
		}
		// order cluase
		query.append(" ORDER BY " + orderClauseBuffer.toString());

		// ONLY FDOR TEST
		// query.append(" limit 100");

		logger.debug("Query for get hierarchies: " + query);
		return query.toString();
	}

	/**
	 * Create query for extracting relations between master and technical hierarchies
	 */
	private String createQueryRelationsHierarchy(IDataSource dataSource, HashMap<String, Object> paramsMap) {

		// 1 - defines select clause and where informations
		String selectClause = getRelationalColumns(dataSource);
		String hierDimensionColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.DIMENSION, dataSource);
		String hierNameMColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.HIER_NM_M, dataSource);
		String hierNodeCdMColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.NODE_CD_M, dataSource);
		String hierBackupColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.BKP_COLUMN, dataSource);

		// select distinct HIER_CD_T, HIER_NM_T, DIMENSION, NODE_CD_T, NODE_NM_T, NODE_LEV_T, HIER_CD_M, HIER_NM_M, NODE_CD_M, NODE_NM_M, NODE_LEV_M
		// from HIER_MASTER_TECHNICAL where HIER_NM_M = 'BPC_PATR' and NODE_CD_M = 'PAS_TP_PC_AP_PRE' and BACKUP = 0
		StringBuffer query = new StringBuffer("SELECT DISTINCT " + selectClause + " FROM " + HierarchyConstants.REL_MASTER_TECH_TABLE_NAME + " WHERE "
				+ hierDimensionColumn + " = \"" + paramsMap.get(HierarchyConstants.DIMENSION) + "\" AND " + hierNameMColumn + " = \""
				+ paramsMap.get(HierarchyConstants.HIER_NM_M) + "\" AND " + hierNodeCdMColumn + " = \"" + paramsMap.get(HierarchyConstants.NODE_CD_M)
				+ "\" AND " + hierBackupColumn + " = 0 ");

		logger.debug("Query for get hierarchies relations: " + query);
		return query.toString();
	}

	private int getTotalNodeFieldsNumber(int totalLevels, List<Field> nodeMetadataFields) {
		int toReturn = 0;

		for (int i = 0, l = nodeMetadataFields.size(); i < l; i++) {
			Field f = nodeMetadataFields.get(i);
			if (f.isSingleValue()) {
				toReturn += 1;
			} else {
				toReturn += totalLevels;
			}
		}

		return toReturn;
	}

	/**
	 * Create HierarchyTreeNode tree from datastore with leafs informations
	 */
	private HierarchyTreeNode createHierarchyTreeStructure(IDataStore dataStore, String dimension, IMetaData metadata) {
		// ONLY FOR DEBUG
		Set<String> allNodeCodes = new HashSet<String>();

		HierarchyTreeNode root = null;

		metadata = dataStore.getMetaData(); // saving metadata for next using

		Hierarchies hierarchies = HierarchiesSingleton.getInstance();
		String prefix = hierarchies.getPrefix(dimension);
		HashMap hierConfig = hierarchies.getConfig(dimension);
		int numLevels = Integer.parseInt((String) hierConfig.get(HierarchyConstants.NUM_LEVELS));
		String rootCode = null;
		// contains the code of the last level node (not null) inserted in the tree
		IMetaData dsMeta = dataStore.getMetaData();

		for (Iterator iterator = dataStore.iterator(); iterator.hasNext();) {
			HierarchyTreeNode localPath = null;
			String lastLevelCodeFound = null;
			String lastLevelNameFound = null;

			IRecord record = (IRecord) iterator.next();
			List<IField> recordFields = record.getFields();
			int fieldsCount = recordFields.size();

			// MAX_DEPTH, must be equal to the level of the leaf (that we skip)
			IField maxDepthField = record.getFieldAt(dsMeta.getFieldIndex(HierarchyConstants.MAX_DEPTH));
			int maxDepth = 0;
			if (maxDepthField.getValue() instanceof Integer) {
				Integer maxDepthValue = (Integer) maxDepthField.getValue();
				maxDepth = maxDepthValue;
			} else if (maxDepthField.getValue() instanceof Long) {
				Long maxDepthValue = (Long) maxDepthField.getValue();
				maxDepth = (int) (long) maxDepthValue;
			}

			int currentLevel = 0;

			for (int i = 1, l = numLevels; i <= l; i++) {
				IField codeField = record.getFieldAt(dsMeta.getFieldIndex(prefix + HierarchyConstants.SUFFIX_CD_LEV + i)); // NODE CODE
				IField nameField = record.getFieldAt(dsMeta.getFieldIndex(prefix + HierarchyConstants.SUFFIX_NM_LEV + i)); // NAME CODE
				IField codeLeafField = record.getFieldAt(dsMeta.getFieldIndex(prefix + HierarchyConstants.SUFFIX_CD_LEAF)); // LEAF CODE
				String leafCode = (String) codeLeafField.getValue();

				if (currentLevel == maxDepth) {
					break; // skip to next iteration
				} else if (codeField.getValue() == null || codeField.getValue().equals("")) {
					// do nothing: it's an empty node
				} else {
					String nodeCode = (String) codeField.getValue();
					String nodeName = (String) nameField.getValue();
					HierarchyTreeNodeData data = new HierarchyTreeNodeData(nodeCode, nodeName);
					// ONLY FOR DEBUG
					if (!allNodeCodes.contains(nodeCode)) {
						allNodeCodes.add(nodeCode);
					}
					// ------------------------

					// update LEVEL && MAX_DEPTH informations
					HashMap mapAttrs = data.getAttributes();
					mapAttrs.put(HierarchyConstants.LEVEL, i);
					mapAttrs.put(HierarchyConstants.MAX_DEPTH, maxDepth);
					data.setAttributes(mapAttrs);

					if (root == null) {
						// get root attribute for automatic edit node GUI
						HashMap rootAttrs = new HashMap();
						ArrayList<Field> generalFields = hierarchies.getHierarchy(dimension).getMetadataGeneralFields();
						for (int f = 0, lf = generalFields.size(); f < lf; f++) {
							Field fld = generalFields.get(f);
							IField fldValue = record.getFieldAt(metadata.getFieldIndex(fld.getId() + ((fld.isSingleValue()) ? "" : i)));
							rootAttrs.put(fld.getId(), (fld.getFixValue() != null) ? fld.getFixValue() : fldValue.getValue());
						}
						rootCode = (String) rootAttrs.get(HierarchyConstants.HIER_CD);
						root = new HierarchyTreeNode(data, rootCode, rootAttrs);

						// ONLY FOR DEBUG
						if (!allNodeCodes.contains(nodeCode)) {
							allNodeCodes.add(nodeCode);
						}
						// ------------------------
					}

					if (localPath == null)
						localPath = new HierarchyTreeNode(data, rootCode, null);

					// check if its a leaf
					if (i == maxDepth) {
						data = setDataValues(dimension, nodeCode, data, record, metadata);
						// update LEVEL informations
						mapAttrs = data.getAttributes();
						mapAttrs.put(HierarchyConstants.LEVEL, i);
						mapAttrs.put(HierarchyConstants.MAX_DEPTH, maxDepth);
						data.setAttributes(mapAttrs);

						// attachNodeToLevel(root, nodeCode, lastLevelCodeFound, localPath, data, allNodeCodes);
						attachNodeToLevel(root, nodeCode, lastLevelCodeFound, data, allNodeCodes);

						lastLevelCodeFound = nodeCode;
						lastLevelNameFound = nodeName;
						break;
					} else if (!root.getKey().contains(nodeCode)) {
						// get nodes attribute for automatic edit node GUI
						ArrayList<Field> nodeFields = hierarchies.getHierarchy(dimension).getMetadataNodeFields();
						for (int f = 0, lf = nodeFields.size(); f < lf; f++) {
							Field fld = nodeFields.get(f);
							IField fldValue = record.getFieldAt(metadata.getFieldIndex(fld.getId() + ((fld.isSingleValue()) ? "" : i)));
							mapAttrs.put(fld.getId(), (fld.getFixValue() != null) ? fld.getFixValue() : fldValue.getValue());
						}
						data.setAttributes(mapAttrs);
						// attachNodeToLevel(root, nodeCode, lastLevelCodeFound, localPath, data, allNodeCodes);
						attachNodeToLevel(root, nodeCode, lastLevelCodeFound, data, allNodeCodes);
					} else {
						// refresh local structure (for parent management)
						HierarchyTreeNode aNodeLocal = new HierarchyTreeNode(data, nodeCode);
						localPath.add(aNodeLocal, nodeCode);
					}
					lastLevelCodeFound = nodeCode;
					lastLevelNameFound = nodeName;
				}
				currentLevel++;
			}

		}

		if (root != null)
			// set debug mode : error is only for debug
			logger.debug(TreeString.toString(root));

		return root;

	}

	/**
	 * Attach a node as a child of another node (with key lastLevelFound that if it's null means a new record and starts from root)
	 */
	// TODO: remove allNodeCodes from signature
	// private void attachNodeToLevel(HierarchyTreeNode root, String nodeCode, String lastLevelFound, HierarchyTreeNode localPath, HierarchyTreeNodeData data,
	private void attachNodeToLevel(HierarchyTreeNode root, String nodeCode, String lastLevelFound, HierarchyTreeNodeData data, Set<String> allNodeCodes) {

		HierarchyTreeNode treeNode = null;
		// get the local element
		// HierarchyTreeNode treeLocalNode = localPath.getHierarchyNode(lastLevelFound, false, null);
		// treeNode = root.getHierarchyNode(lastLevelFound, true);

		// first search parent node (with all path)

		int localLevel = 0;
		for (Iterator<HierarchyTreeNode> treeIterator = root.iterator(); treeIterator.hasNext();) {
			localLevel++;
			Integer nodeLevel = ((Integer) data.getAttributes().get(HierarchyConstants.LEVEL));
			Integer maxDeptLevel = ((Integer) data.getAttributes().get(HierarchyConstants.MAX_DEPTH));
			// treeNode = treeIterator.next();
			treeNode = root.getHierarchyNode(lastLevelFound, true, nodeLevel - 1);

			if (lastLevelFound == null) {
				break;
			} else if (treeNode.getKey().equals(lastLevelFound) && localLevel == nodeLevel) {
				// parent node found
				break;
			}
			// // update the local structure for next elements
			// HierarchyTreeNode aNodeLocal = new HierarchyTreeNode(data, nodeCode);
			// treeLocalNode.add(aNodeLocal, nodeCode); // updates the local path
		}
		// then check if node was already added as a child of this parent
		if (!treeNode.getChildrensKeys().contains(nodeCode)) {
			// node not already attached to the level
			HierarchyTreeNode aNode = new HierarchyTreeNode(data, nodeCode);
			treeNode.add(aNode, nodeCode);
		}

		// ONLY FOR DEBUG
		if (allNodeCodes.contains(nodeCode)) {
			// logger.error("COLLISION DETECTED ON: " + nodeCode);
		} else {
			allNodeCodes.add(nodeCode);
		}

	}

	/**
	 * Sets records' value to the tree structure (leaf informations, date and strings)
	 */
	private HierarchyTreeNodeData setDataValues(String dimension, String nodeCode, HierarchyTreeNodeData data, IRecord record, IMetaData metadata) {
		// inject leafID into node

		Hierarchies hierarchies = HierarchiesSingleton.getInstance();

		IField leafIdField = record.getFieldAt(metadata.getFieldIndex(hierarchies.getHierarchyTableForeignKeyName(dimension)));
		String leafIdString = null;
		if (leafIdField.getValue() instanceof Integer) {
			Integer leafId = (Integer) leafIdField.getValue();
			leafIdString = String.valueOf(leafId);
		} else if (leafIdField.getValue() instanceof Long) {
			Long leafId = (Long) leafIdField.getValue();
			leafIdString = String.valueOf(leafId);
		}
		data.setLeafId(leafIdString);

		IField leafParentCodeField = record.getFieldAt(metadata.getFieldIndex(HierarchyConstants.LEAF_PARENT_CD));
		String leafParentCodeString = (String) leafParentCodeField.getValue();
		data.setNodeCode(leafParentCodeString + "_" + nodeCode);
		nodeCode = leafParentCodeString + "_" + nodeCode;
		data.setLeafParentCode(leafParentCodeString);
		// data.setLeafOriginalParentCode(leafParentCodeString); // backup code

		IField leafParentNameField = record.getFieldAt(metadata.getFieldIndex(HierarchyConstants.LEAF_PARENT_NM));
		String leafParentNameString = (String) leafParentNameField.getValue();
		data.setLeafParentName(leafParentNameString);

		IField beginDtField = record.getFieldAt(metadata.getFieldIndex(HierarchyConstants.BEGIN_DT));
		Date beginDtDate = (Date) beginDtField.getValue();
		data.setBeginDt(beginDtDate);

		IField endDtField = record.getFieldAt(metadata.getFieldIndex(HierarchyConstants.END_DT));
		Date endDtDate = (Date) endDtField.getValue();
		data.setEndDt(endDtDate);

		HashMap mapAttrs = new HashMap();
		int numLevels = Integer.valueOf((String) hierarchies.getConfig(dimension).get(HierarchyConstants.NUM_LEVELS));
		Integer maxDepth = (Integer) (record.getFieldAt(metadata.getFieldIndex(HierarchyConstants.MAX_DEPTH)).getValue());

		// add leaf field attributes for automatic edit field GUI
		ArrayList<Field> leafFields = hierarchies.getHierarchy(dimension).getMetadataLeafFields();
		for (int f = 0, lf = leafFields.size(); f < lf; f++) {
			Field fld = leafFields.get(f);
			String idFld = fld.getId();
			if (!fld.isSingleValue()) {
				IField fldValue = record.getFieldAt(metadata.getFieldIndex(idFld + maxDepth));
				mapAttrs.put(idFld, (fld.getFixValue() != null) ? fld.getFixValue() : fldValue.getValue());
			} else {
				IField fldValue = record.getFieldAt(metadata.getFieldIndex(idFld));
				mapAttrs.put(idFld, (fld.getFixValue() != null) ? fld.getFixValue() : fldValue.getValue());
			}
		}
		data.setAttributes(mapAttrs);
		return data;
	}

	private JSONObject convertHierarchyTreeAsJSON(HierarchyTreeNode root, String hierName, String dimension) {
		JSONArray rootJSONObject = new JSONArray();

		if (root == null)
			return null;

		try {
			Hierarchies hierarchies = HierarchiesSingleton.getInstance();
			HashMap hierConfig = hierarchies.getConfig(dimension);

			HierarchyTreeNodeData rootData = (HierarchyTreeNodeData) root.getObject();
			JSONArray childrenJSONArray = new JSONArray();

			String hierTp = (String) root.getAttributes().get(HierarchyConstants.HIER_TP);

			for (int i = 0; i < root.getChildCount(); i++) {
				HierarchyTreeNode childNode = root.getChild(i);
				JSONObject subTreeJSONObject = getSubTreeJSONObject(childNode, hierConfig, hierTp, hierName);
				childrenJSONArray.put(subTreeJSONObject);
			}

			rootJSONObject.put(childrenJSONArray);

			JSONObject mainObject = new JSONObject();
			mainObject.put(HierarchyConstants.TREE_NAME, root.getKey());
			mainObject.put(HierarchyConstants.ID, "root");
			mainObject.put("aliasId", HierarchyConstants.HIER_CD);
			mainObject.put("aliasName", HierarchyConstants.HIER_NM);
			mainObject.put("root", true);
			mainObject.put("children", childrenJSONArray);
			mainObject.put("leaf", false);
			HashMap rootAttrs = root.getAttributes();
			HierarchyUtils.createJSONArrayFromHashMap(rootAttrs, mainObject);

			return mainObject;

		} catch (Throwable t) {
			throw new SpagoBIServiceException("An unexpected error occured while retriving hierarchy structure", t);
		}

	}

	/**
	 * get the JSONObject representing the tree having the passed node as a root
	 *
	 * @param node
	 *            the root of the subtree
	 * @return JSONObject representing the subtree
	 */
	private JSONObject getSubTreeJSONObject(HierarchyTreeNode node, HashMap hierConfig, String hierTp, String hierNm) {

		try {
			HierarchyTreeNodeData nodeData = (HierarchyTreeNodeData) node.getObject();
			JSONObject nodeJSONObject = new JSONObject();
			int level = (Integer) nodeData.getAttributes().get(HierarchyConstants.LEVEL);
			int maxDepth = (Integer) nodeData.getAttributes().get(HierarchyConstants.MAX_DEPTH);

			if (node.getChildCount() > 0) {
				// if (level < maxDepth) {
				// it's a node or a leaf with the same code of the folder
				nodeJSONObject.put(HierarchyConstants.TREE_NAME, nodeData.getNodeName());
				nodeJSONObject.put(HierarchyConstants.ID, nodeData.getNodeCode());
				nodeJSONObject.put(HierarchyConstants.LEAF_ID, nodeData.getLeafId());
				nodeJSONObject.put(HierarchyConstants.LEAF_PARENT_CD, nodeData.getLeafParentCode());
				// nodeJSONObject.put(HierarchyConstants.LEAF_ORIG_PARENT_CD, nodeData.getLeafOriginalParentCode());
				nodeJSONObject.put(HierarchyConstants.LEAF_PARENT_NM, nodeData.getLeafParentName());
				nodeJSONObject.put("aliasId", hierConfig.get(HierarchyConstants.TREE_NODE_CD));
				nodeJSONObject.put("aliasName", hierConfig.get(HierarchyConstants.TREE_NODE_NM));

				JSONArray childrenJSONArray = new JSONArray();

				for (int i = 0; i < node.getChildCount(); i++) {
					HierarchyTreeNode childNode = node.getChild(i);
					JSONObject subTree = getSubTreeJSONObject(childNode, hierConfig, hierTp, hierNm);
					childrenJSONArray.put(subTree);
				}
				nodeJSONObject.put("children", childrenJSONArray);
				nodeJSONObject.put("leaf", false);

				nodeJSONObject = setDetailsInfo(nodeJSONObject, nodeData);
				return nodeJSONObject;

			} else {
				// it's a leaf
				nodeJSONObject.put(HierarchyConstants.TREE_NAME, nodeData.getNodeName());
				nodeJSONObject.put(HierarchyConstants.ID, nodeData.getNodeCode());
				nodeJSONObject.put(HierarchyConstants.LEAF_ID, nodeData.getLeafId());
				nodeJSONObject.put(HierarchyConstants.LEAF_PARENT_CD, nodeData.getLeafParentCode());
				// nodeJSONObject.put(HierarchyConstants.LEAF_ORIG_PARENT_CD, nodeData.getLeafOriginalParentCode());
				nodeJSONObject.put(HierarchyConstants.LEAF_PARENT_NM, nodeData.getLeafParentName());
				nodeJSONObject.put("aliasId", hierConfig.get(HierarchyConstants.TREE_LEAF_CD));
				nodeJSONObject.put("aliasName", hierConfig.get(HierarchyConstants.TREE_LEAF_NM));
				nodeJSONObject.put("leaf", true);

				// adds informations for propagation management
				String tpSuffix = "_" + hierTp.substring(0, 1);
				nodeJSONObject.put(HierarchyConstants.HIER_TP + tpSuffix, hierTp);
				nodeJSONObject.put(HierarchyConstants.HIER_NM + tpSuffix, hierNm);
				nodeJSONObject.put("NODE_CD" + tpSuffix, nodeData.getLeafParentCode());
				nodeJSONObject.put("NODE_NM" + tpSuffix, nodeData.getLeafParentName());
				nodeJSONObject.put("NODE_LEV" + tpSuffix, level - 1);

				nodeJSONObject = setDetailsInfo(nodeJSONObject, nodeData);
				return nodeJSONObject;

			}
		} catch (Throwable t) {
			throw new SpagoBIServiceException("An unexpected error occured while serializing hierarchy structure to JSON", t);
		}

	}

	private JSONObject setDetailsInfo(JSONObject nodeJSONObject, HierarchyTreeNodeData nodeData) {
		try {
			JSONObject toReturn = nodeJSONObject;

			toReturn.put(HierarchyConstants.BEGIN_DT, nodeData.getBeginDt());
			toReturn.put(HierarchyConstants.END_DT, nodeData.getEndDt());

			HashMap mapAttrs = nodeData.getAttributes();
			HierarchyUtils.createJSONArrayFromHashMap(mapAttrs, toReturn);

			return toReturn;
		} catch (Throwable t) {
			throw new SpagoBIServiceException("An unexpected error occured while serializing hierarchy details structure to JSON", t);
		}

	}

	/**
	 * Persist custom hierarchy paths to database
	 */
	private void persistHierarchyPath(Connection connection, IDataSource dataSource, HashMap<String, Object> paramsMap, List<HierarchyTreeNodeData> path,
			Hierarchy hierarchyFields, HashMap hierConfig) throws SQLException {
		String columns = "";
		try {
			// 1 - get fields structure
			List<Field> generalMetadataFields = new ArrayList<Field>(hierarchyFields.getMetadataGeneralFields());
			List<Field> nodeMetadataFields = new ArrayList<Field>(hierarchyFields.getMetadataNodeFields());
			List<Field> leafMetadataFields = new ArrayList<Field>(hierarchyFields.getMetadataLeafFields());

			// 2 - get total columns number
			int totalColumns = 0;
			int totalLevels = Integer.parseInt((String) hierConfig.get(HierarchyConstants.NUM_LEVELS));
			int totalGeneralFields = generalMetadataFields.size();
			int totalLeafFields = leafMetadataFields.size();
			int totalNodeFields = getTotalNodeFieldsNumber(totalLevels, nodeMetadataFields);

			totalColumns = totalGeneralFields + totalLeafFields + totalNodeFields;
			int numLevels = Integer.valueOf((String) hierConfig.get(HierarchyConstants.NUM_LEVELS));

			// 3 - Insert prepared statement construction
			// ------------------------------------------
			StringBuffer sbColumns = new StringBuffer();
			LinkedHashMap<String, String> lstFields = new LinkedHashMap<String, String>();

			for (int i = 0, l = generalMetadataFields.size(); i < l; i++) {
				String column = "";
				Field f = generalMetadataFields.get(i);
				String sep = ", ";
				if (!f.isSingleValue()) {
					for (int idx = 1; idx <= numLevels; idx++) {
						String nameF = f.getId() + idx;
						column = AbstractJDBCDataset.encapsulateColumnName(nameF, dataSource);
						sbColumns.append(column + sep);
						lstFields.put(nameF, f.getType());
					}
				} else {
					lstFields.put(f.getId(), f.getType());
					column = AbstractJDBCDataset.encapsulateColumnName(f.getId(), dataSource);
					sbColumns.append(column + sep);
				}
			}

			for (int i = 0, l = nodeMetadataFields.size(); i < l; i++) {
				String column = "";
				Field f = nodeMetadataFields.get(i);
				String sep = ", ";
				if (!f.isSingleValue()) {
					for (int idx = 1; idx <= numLevels; idx++) {
						String nameF = f.getId() + idx;
						column = AbstractJDBCDataset.encapsulateColumnName(nameF, dataSource);
						sbColumns.append(column + sep);
						lstFields.put(nameF, f.getType());
					}
				} else {
					lstFields.put(f.getId(), f.getType());
					column = AbstractJDBCDataset.encapsulateColumnName(f.getId(), dataSource);
					sbColumns.append(column + sep);
				}
			}

			for (int i = 0, l = leafMetadataFields.size(); i < l; i++) {
				String column = "";
				Field f = leafMetadataFields.get(i);
				String sep = (i == l - 1) ? "" : ", ";
				if (!f.isSingleValue()) {
					for (int idx = 1; idx <= numLevels; idx++) {
						String nameF = f.getId() + idx;
						column = AbstractJDBCDataset.encapsulateColumnName(nameF, dataSource);
						sbColumns.append(column + sep);
						lstFields.put(nameF, f.getType());
					}
				} else {
					lstFields.put(f.getId(), f.getType());
					column = AbstractJDBCDataset.encapsulateColumnName(f.getId(), dataSource);
					sbColumns.append(column + sep);
				}
			}

			columns = sbColumns.toString();

			String insertQuery = "insert into " + (String) paramsMap.get("hierarchyTable") + "(" + columns + ") values (";
			for (int c = 0, lc = totalColumns; c < lc; c++) {
				insertQuery += "?" + ((c < lc - 1) ? ", " : " ");
			}
			insertQuery += ")";

			// preparedStatement for insert into HIER_XXX
			PreparedStatement hierPreparedStatement = connection.prepareStatement(insertQuery);

			// 4 - Valorization of DEFUALT for prepared statement placeholders
			// -----------------------------------------------
			for (int i = 1; i <= lstFields.size(); i++) {
				hierPreparedStatement.setObject(i, null);
			}

			// 4 - Explore the path and set the corresponding columns for insert hier and insert rel (master and technical)
			// -----------------------------------------------
			for (int i = 0; i < path.size(); i++) {
				HierarchyTreeNodeData node = path.get(i);
				hierPreparedStatement = valorizeInsertPlaceholders(hierPreparedStatement, node, lstFields, paramsMap);
				if ((boolean) paramsMap.get("doPropagation")) {
					persistRelationMasterTechnical(connection, node, dataSource, paramsMap);
				}
			}

			// 5 - Execution of insert prepared statement
			// -----------------------------------------------
			hierPreparedStatement.executeUpdate();
			hierPreparedStatement.close();

			// 6 - Insert relations from MASTER and TECHNICAL
			// ----------------------------------------------

		} catch (Throwable t) {
			throw new SpagoBIServiceException("An unexpected error occured while persisting hierarchy structure", t.getMessage());
		}
	}

	/**
	 * Persist informations about relation between the master node and the technical node
	 *
	 * @param connection
	 * @param node
	 * @param dataSource
	 * @throws SQLException
	 */
	private void persistRelationMasterTechnical(Connection connection, HierarchyTreeNodeData node, IDataSource dataSource, HashMap paramsMap)
			throws SQLException {
		try {
			// prepare stmt ONLY for original MASTER (with '_M' suffix) nodes
			if (node.getAttributes().get(HierarchyConstants.HIER_NM_M) != null) {
				String relColumns = getRelationalColumns(dataSource);
				String insertRelQuery = "insert into " + HierarchyConstants.REL_MASTER_TECH_TABLE_NAME + " (" + relColumns
						+ ") values (?,?,?,?,?,?,?,?,?,?,?) ";
				try (PreparedStatement relPreparedStatement = connection.prepareStatement(insertRelQuery);) {
					for (int k = 1; k <= 11; k++) {
						relPreparedStatement.setObject(k, null);
					}
					valorizeRelPlaceholders(relPreparedStatement, node, paramsMap);
					relPreparedStatement.executeUpdate();
					// relPreparedStatement.close();
				} catch (Throwable t2) {
					logger.error("An unexpected error occured while updating hierarchy for backup");
					throw new SpagoBIServiceException("An unexpected error occured while updating hierarchy for backup", t2);
				}
			}
		} catch (Throwable t) {
			throw new SpagoBIServiceException("An unexpected error occured while persisting hierarchy relations", t.getMessage());
		}
	}

	/**
	 * Returns a string with columns list for stmt
	 *
	 * @param dataSource
	 *            the datasource
	 * @return String
	 */
	private String getRelationalColumns(IDataSource dataSource) {

		String toReturn = null;

		StringBuffer sbColumns = new StringBuffer();
		String column = null;

		column = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.DIMENSION, dataSource);
		sbColumns.append(column + ",");
		column = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.HIER_CD_T, dataSource);
		sbColumns.append(column + ",");
		column = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.HIER_NM_T, dataSource);
		sbColumns.append(column + ",");
		column = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.NODE_CD_T, dataSource);
		sbColumns.append(column + ",");
		column = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.NODE_NM_T, dataSource);
		sbColumns.append(column + ",");
		column = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.NODE_LEV_T, dataSource);
		sbColumns.append(column + ",");
		column = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.HIER_CD_M, dataSource);
		sbColumns.append(column + ",");
		column = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.HIER_NM_M, dataSource);
		sbColumns.append(column + ",");
		column = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.NODE_CD_M, dataSource);
		sbColumns.append(column + ",");
		column = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.NODE_NM_M, dataSource);
		sbColumns.append(column + ",");
		column = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.NODE_LEV_M, dataSource);
		sbColumns.append(column);

		toReturn = sbColumns.toString();
		return toReturn;
	}

	/**
	 * set values for the preparedStatement of INSERT into the HIER_XXX
	 */
	private PreparedStatement valorizeInsertPlaceholders(PreparedStatement preparedStatement, HierarchyTreeNodeData node, LinkedHashMap lstFields,
			HashMap paramsMap) throws SQLException {

		PreparedStatement toReturn = preparedStatement;
		HashMap values = new HashMap();

		try {
			boolean isRoot = ((Boolean) node.getAttributes().get("isRoot")).booleanValue();
			boolean isLeaf = ((Boolean) node.getAttributes().get("isLeaf")).booleanValue();
			String hierarchyPrefix = (String) paramsMap.get("hierarchyPrefix");
			if (isLeaf) {
				// it's a leaf
				toReturn.setString(getPosField(lstFields, hierarchyPrefix + HierarchyConstants.SUFFIX_CD_LEAF), node.getNodeCode());
				values.put(hierarchyPrefix + HierarchyConstants.SUFFIX_CD_LEAF, node.getNodeCode());
				toReturn.setString(getPosField(lstFields, hierarchyPrefix + HierarchyConstants.SUFFIX_NM_LEAF), node.getNodeName());
				values.put(hierarchyPrefix + HierarchyConstants.SUFFIX_NM_LEAF, node.getNodeName());
				if (node.getDepth() != null) {
					toReturn.setString(getPosField(lstFields, hierarchyPrefix + HierarchyConstants.SUFFIX_CD_LEV + node.getDepth()), node.getNodeCode());
					values.put(hierarchyPrefix + HierarchyConstants.SUFFIX_CD_LEV, node.getNodeCode());
				}
				if (node.getDepth() != null) {
					toReturn.setString(getPosField(lstFields, hierarchyPrefix + HierarchyConstants.SUFFIX_NM_LEV + node.getDepth()), node.getNodeName());
					values.put(hierarchyPrefix + HierarchyConstants.SUFFIX_CD_LEV, node.getNodeName());
				} else if (!isRoot) {
					logger.error("Property LEVEL non found for leaf element with code " + node.getNodeCode() + " and name " + node.getNodeName());
					throw new SpagoBIServiceException("persistService", "Property LEVEL non found for leaf element with code " + node.getNodeCode()
							+ " and name " + node.getNodeName());
				}
				toReturn.setString(getPosField(lstFields, hierarchyPrefix + HierarchyConstants.SUFFIX_NM_LEAF), node.getNodeName());
				values.put(hierarchyPrefix + HierarchyConstants.SUFFIX_NM_LEAF, node.getNodeName());
				toReturn.setObject(getPosField(lstFields, hierarchyPrefix + "_" + HierarchyConstants.LEAF_ID), node.getLeafId());
				values.put(hierarchyPrefix + "_" + HierarchyConstants.LEAF_ID, node.getLeafId());
				toReturn.setObject(getPosField(lstFields, HierarchyConstants.LEAF_PARENT_CD), node.getLeafParentCode());
				values.put(HierarchyConstants.LEAF_PARENT_CD, node.getLeafParentCode());
				// preparedStatement.setObject(getPosField(lstFields, HierarchyConstants.LEAF_ORIG_PARENT_CD), node.getLeafOriginalParentCode());
				toReturn.setObject(getPosField(lstFields, HierarchyConstants.LEAF_PARENT_NM), node.getLeafParentName());
				values.put(HierarchyConstants.LEAF_PARENT_NM, node.getLeafParentName());
				toReturn.setObject(getPosField(lstFields, HierarchyConstants.MAX_DEPTH), node.getDepth());
				values.put(HierarchyConstants.MAX_DEPTH, node.getDepth());
				toReturn.setObject(getPosField(lstFields, HierarchyConstants.BEGIN_DT), node.getBeginDt());
				values.put(HierarchyConstants.BEGIN_DT, node.getBeginDt());
				toReturn.setObject(getPosField(lstFields, HierarchyConstants.END_DT), node.getEndDt());
				values.put(HierarchyConstants.END_DT, node.getEndDt());

				// get other leaf's attributes (not mandatory)
				Iterator iter = node.getAttributes().keySet().iterator();
				while (iter.hasNext()) {
					String key = (String) iter.next();
					Object value = node.getAttributes().get(key);
					if (key != null && value != null) {
						int attrPos = getPosField(lstFields, key);
						if (attrPos != -1) {
							preparedStatement.setObject(attrPos, value);
							values.put(key, value);
						}
					}
				}
			} else {
				// not-leaf node
				int level = 0;
				// get other node's attributes (not mandatory ie sign)
				Iterator iter = node.getAttributes().keySet().iterator();
				String strLevel = (String) node.getAttributes().get(HierarchyConstants.LEVEL);
				level = (strLevel != null) ? Integer.parseInt(strLevel) : 0;
				if (level == 0 && !isRoot) {
					logger.error("Property LEVEL non found for node element with code: [" + node.getNodeCode() + "] - name: [" + node.getNodeName() + "]");
					throw new SpagoBIServiceException("persistService", "Property LEVEL non found for node element with code " + node.getNodeCode()
							+ " and name " + node.getNodeName());
				}
				while (iter.hasNext()) {
					String key = (String) iter.next();
					Object value = node.getAttributes().get(key);
					if (key != null && value != null) {
						int attrPos = getPosField(lstFields, key);
						if (attrPos == -1)
							attrPos = getPosField(lstFields, key + level);
						if (attrPos != -1) {
							preparedStatement.setObject(attrPos, value);
							values.put(key, value);
						}
					}
				}
				if (level > 0) {
					toReturn.setString(getPosField(lstFields, hierarchyPrefix + HierarchyConstants.SUFFIX_CD_LEV + level), node.getNodeCode());
					values.put(hierarchyPrefix + HierarchyConstants.SUFFIX_CD_LEV, node.getNodeCode());
					toReturn.setString(getPosField(lstFields, hierarchyPrefix + HierarchyConstants.SUFFIX_NM_LEV + level), node.getNodeName());
					values.put(hierarchyPrefix + HierarchyConstants.SUFFIX_NM_LEV, node.getNodeName());
				}
			}
		} catch (Throwable t) {
			String errMsg = "Error while inserting element with code: [" + node.getNodeCode() + "] and name: [" + node.getNodeName() + "]";
			if (values.size() > 0) {
				errMsg += " with next values: [";
				Iterator iter = values.keySet().iterator();
				while (iter.hasNext()) {
					String key = (String) iter.next();
					Object value = values.get(key);
					errMsg += " key: " + key + " - value: " + value + ((iter.hasNext()) ? "," : "]");
				}
				logger.error(errMsg, t);
			}
			throw new SpagoBIServiceException("An unexpected error occured while persisting hierarchy structure", t.getMessage() + " - " + errMsg);
		}

		return toReturn;
	}

	/**
	 * set values for the preparedStatement of relations between MASTER and TECHNICAL (HIER_XXX)
	 */
	private PreparedStatement valorizeRelPlaceholders(PreparedStatement preparedStatement, HierarchyTreeNodeData node, HashMap paramsMap) throws SQLException {

		PreparedStatement toReturn = preparedStatement;

		HashMap values = new HashMap();

		try {
			toReturn.setObject(1, paramsMap.get("dimension"));
			values.put("DIMENSION", paramsMap.get("dimension"));

			toReturn.setObject(2, paramsMap.get("hierTargetCode"));
			values.put("HIER_CD_T", paramsMap.get("hierTargetCode"));

			toReturn.setObject(3, paramsMap.get("hierTargetName"));
			values.put(HierarchyConstants.HIER_NM_T, paramsMap.get("hierTargetName"));

			toReturn.setObject(4, node.getLeafParentCode());
			values.put(HierarchyConstants.NODE_CD_T, node.getLeafParentCode());

			toReturn.setObject(5, node.getLeafParentName());
			values.put(HierarchyConstants.NODE_NM_T, node.getLeafParentName());

			toReturn.setObject(6, Integer.valueOf((String) node.getAttributes().get(HierarchyConstants.LEVEL)) - 1);
			values.put(HierarchyConstants.NODE_LEV_T, Integer.valueOf((String) node.getAttributes().get(HierarchyConstants.LEVEL)) - 1);

			toReturn.setObject(7, paramsMap.get("hierSourceCode"));
			values.put(HierarchyConstants.HIER_CD_M, paramsMap.get("hierSourceCode"));

			toReturn.setObject(8, paramsMap.get("hierSourceName"));
			values.put(HierarchyConstants.HIER_NM_M, paramsMap.get("hierSourceName"));

			toReturn.setObject(9, node.getAttributes().get(HierarchyConstants.NODE_CD_M));
			values.put(HierarchyConstants.NODE_CD_M, node.getAttributes().get(HierarchyConstants.NODE_CD_M));

			toReturn.setObject(10, node.getAttributes().get(HierarchyConstants.NODE_NM_M));
			values.put(HierarchyConstants.NODE_NM_M, node.getAttributes().get(HierarchyConstants.NODE_NM_M));

			toReturn.setObject(11, node.getAttributes().get(HierarchyConstants.NODE_LEV_M));
			values.put(HierarchyConstants.NODE_LEV_M, node.getAttributes().get(HierarchyConstants.NODE_LEV_M));

		} catch (Throwable t) {
			String errMsg = "Error while inserting relation of element with code: [" + node.getNodeCode() + "] and name: [" + node.getNodeName() + "]";
			if (values.size() > 0) {
				errMsg += " with next values: [";
				Iterator iter = values.keySet().iterator();
				while (iter.hasNext()) {
					String key = (String) iter.next();
					Object value = values.get(key);
					errMsg += " key: " + key + " - value: " + value + ((iter.hasNext()) ? "," : "]");
				}
				logger.error(errMsg, t);
			}
			throw new SpagoBIServiceException("An unexpected error occured while persisting hierarchy structure: ", t.getMessage() + " - " + t.getCause()
					+ " - " + errMsg);
		}

		return toReturn;
	}

	/**
	 *
	 * @param lstFields
	 * @param name
	 * @return the position of the field with the input name in order to the stmt
	 */
	private int getPosField(LinkedHashMap<String, String> lstFields, String name) {
		int toReturn = 1;

		for (String key : lstFields.keySet()) {
			if (key.equalsIgnoreCase(name))
				return toReturn;

			toReturn++;
		}
		logger.info("Attribute '" + name + "' non found in fields' list ");
		return -1;
	}

	/**
	 * Find all paths from root to leaves
	 */
	private Collection<List<HierarchyTreeNodeData>> findRootToLeavesPaths(JSONObject node, String dimension) {
		Collection<List<HierarchyTreeNodeData>> collectionOfPaths = new HashSet<List<HierarchyTreeNodeData>>();
		try {
			Hierarchies hierarchies = HierarchiesSingleton.getInstance();
			String hierarchyPrefix = hierarchies.getPrefix(dimension);

			String nodeName = node.getString(HierarchyConstants.TREE_NAME);
			String nodeCode = node.getString(HierarchyConstants.ID);

			HashMap mapAttrs = new HashMap();
			if (!node.isNull(HierarchyConstants.LEVEL)) {
				mapAttrs.put(HierarchyConstants.LEVEL, node.getString(HierarchyConstants.LEVEL));
			}
			// add other general attributes if they are valorized
			ArrayList<Field> generalFields = hierarchies.getHierarchy(dimension).getMetadataGeneralFields();
			for (int f = 0, lf = generalFields.size(); f < lf; f++) {
				Field fld = generalFields.get(f);
				String idFld = fld.getId();
				if (!node.isNull(idFld)) {
					mapAttrs.put(idFld, node.getString(idFld));
				}
			}
			// add other node attributes if they are valorized
			ArrayList<Field> nodeFields = hierarchies.getHierarchy(dimension).getMetadataNodeFields();
			for (int f = 0, lf = nodeFields.size(); f < lf; f++) {
				Field fld = nodeFields.get(f);
				String idFld = fld.getId();
				if (!node.isNull(idFld)) {
					mapAttrs.put(idFld, node.getString(idFld));
				}
			}
			// add other leaf attributes if they are valorized
			ArrayList<Field> leafFields = hierarchies.getHierarchy(dimension).getMetadataLeafFields();
			for (int f = 0, lf = nodeFields.size(); f < lf; f++) {
				Field fld = nodeFields.get(f);
				String idFld = fld.getId();
				if (!node.isNull(idFld)) {
					mapAttrs.put(idFld, node.getString(idFld));
				}
			}
			// current node is a root?
			boolean isRoot = (node.isNull("root")) ? false : node.getBoolean("root");
			mapAttrs.put("isRoot", isRoot);

			// current node is a leaf?
			boolean isLeaf = node.getBoolean("leaf");
			mapAttrs.put("isLeaf", isLeaf);

			// adds as attributes properties about MASTER references (propagation management)
			if (!node.isNull(HierarchyConstants.HIER_CD_M))
				mapAttrs.put(HierarchyConstants.HIER_CD_M, node.getString(HierarchyConstants.HIER_CD_M));
			if (!node.isNull(HierarchyConstants.HIER_NM_M))
				mapAttrs.put(HierarchyConstants.HIER_NM_M, node.getString(HierarchyConstants.HIER_NM_M));
			if (!node.isNull(HierarchyConstants.NODE_CD_M))
				mapAttrs.put(HierarchyConstants.NODE_CD_M, node.getString(HierarchyConstants.NODE_CD_M));
			if (!node.isNull(HierarchyConstants.NODE_NM_M))
				mapAttrs.put(HierarchyConstants.NODE_NM_M, node.getString(HierarchyConstants.NODE_NM_M));
			if (!node.isNull(HierarchyConstants.NODE_LEV_M))
				mapAttrs.put(HierarchyConstants.NODE_LEV_M, node.getString(HierarchyConstants.NODE_LEV_M));

			String nodeLeafId = !node.isNull(HierarchyConstants.LEAF_ID) ? node.getString(HierarchyConstants.LEAF_ID) : "";
			if (nodeLeafId.equals("")) {
				nodeLeafId = (mapAttrs.get(hierarchyPrefix + "_" + HierarchyConstants.LEAF_ID) != null) ? (String) mapAttrs.get(hierarchyPrefix + "_"
						+ HierarchyConstants.LEAF_ID) : "";
			}
			if (nodeLeafId.equals("") && !node.isNull(hierarchyPrefix + "_" + HierarchyConstants.FIELD_ID)) {
				nodeLeafId = node.getString(hierarchyPrefix + "_" + HierarchyConstants.FIELD_ID); // dimension id (ie: ACCOUNT_ID)
			}
			// create node
			HierarchyTreeNodeData nodeData = new HierarchyTreeNodeData(nodeCode, nodeName, nodeLeafId, "", "", "", mapAttrs);

			if (isLeaf) {
				List<HierarchyTreeNodeData> aPath = new ArrayList<HierarchyTreeNodeData>();
				if (!node.isNull(hierarchyPrefix + HierarchyConstants.SUFFIX_CD_LEAF))
					nodeData.setNodeCode(node.getString(hierarchyPrefix + HierarchyConstants.SUFFIX_CD_LEAF));
				if (!node.isNull(hierarchyPrefix + HierarchyConstants.SUFFIX_NM_LEAF))
					nodeData.setNodeName(node.getString(hierarchyPrefix + HierarchyConstants.SUFFIX_NM_LEAF));
				if (!node.isNull(HierarchyConstants.BEGIN_DT)) {
					nodeData.setBeginDt(Date.valueOf(node.getString(HierarchyConstants.BEGIN_DT)));
				}
				if (!node.isNull(HierarchyConstants.END_DT)) {
					nodeData.setEndDt(Date.valueOf(node.getString(HierarchyConstants.END_DT)));
				}
				// set parent informations
				String nodeParentCode = null;
				// String nodeOriginalParentCode = null;
				if (!node.isNull(HierarchyConstants.LEAF_PARENT_CD))
					nodeParentCode = node.getString(HierarchyConstants.LEAF_PARENT_CD);
				// if (!node.isNull(HierarchyConstants.LEAF_ORIG_PARENT_CD))
				// nodeOriginalParentCode = node.getString(HierarchyConstants.LEAF_ORIG_PARENT_CD);
				// nodeData.setNodeCode(nodeCode.replaceFirst(nodeOriginalParentCode + "_", ""));
				nodeData.setLeafParentCode(nodeParentCode);
				nodeData.setLeafParentName(node.getString(HierarchyConstants.LEAF_PARENT_NM));
				// nodeData.setLeafOriginalParentCode(nodeOriginalParentCode);
				nodeData.setDepth(node.getString(HierarchyConstants.LEVEL));
				aPath.add(nodeData);
				collectionOfPaths.add(aPath);
				return collectionOfPaths;
			} else {
				// node has children
				JSONArray childs = node.getJSONArray("children");
				for (int i = 0; i < childs.length(); i++) {
					JSONObject child = childs.getJSONObject(i);
					Collection<List<HierarchyTreeNodeData>> childPaths = findRootToLeavesPaths(child, dimension);
					for (List<HierarchyTreeNodeData> path : childPaths) {
						// add this node to start of the path
						path.add(0, nodeData);
						collectionOfPaths.add(path);
					}
				}
			}
			return collectionOfPaths;
		} catch (JSONException je) {
			logger.error("An unexpected error occured while retriving hierarchy root-leafs paths");
			throw new SpagoBIServiceException("An unexpected error occured while retriving custom hierarchy root-leafs paths", je);
		} catch (Throwable t) {
			logger.error("An unexpected error occured while retriving hierarchy root-leafs paths");
			throw new SpagoBIServiceException("An unexpected error occured while retriving hierarchy root-leafs paths", t);
		}

	}

	private void updateHierarchyForBackup(IDataSource dataSource, Connection databaseConnection, HashMap paramsMap) {
		logger.debug("START");

		String hierNameColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.HIER_NM, dataSource);
		String beginDtColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.BEGIN_DT, dataSource);
		String endDtColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.END_DT, dataSource);
		String hierTypeColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.HIER_TP, dataSource);
		String bkpColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.BKP_COLUMN, dataSource);
		String bkpTimestampColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.BKP_TIMESTAMP_COLUMN, dataSource);

		Calendar calendar = Calendar.getInstance();
		calendar.setTime(calendar.getTime());
		long timestamp = calendar.getTimeInMillis();

		Date vDateConverted = Date.valueOf((String) paramsMap.get("validityDate"));

		String vDateWhereClause = " ? >= " + beginDtColumn + " AND ? <= " + endDtColumn;

		String updateQuery = "UPDATE " + (String) paramsMap.get("hierarchyTable") + " SET " + hierNameColumn + "= ?, " + bkpColumn + " = ?, "
				+ bkpTimestampColumn + "= ? WHERE " + hierNameColumn + "=? AND " + hierTypeColumn + "= ? AND " + vDateWhereClause;

		logger.debug("The update query is [" + updateQuery + "]");

		try (Statement stmt = databaseConnection.createStatement(); PreparedStatement preparedStatement = databaseConnection.prepareStatement(updateQuery)) {
			preparedStatement.setString(1, (String) paramsMap.get("hierSourceName") + "_" + timestamp);
			preparedStatement.setBoolean(2, true);
			preparedStatement.setTimestamp(3, new java.sql.Timestamp(timestamp));
			preparedStatement.setString(4, (String) paramsMap.get("hierTargetName"));
			preparedStatement.setString(5, (String) paramsMap.get("hierTargetType"));
			preparedStatement.setDate(6, vDateConverted);
			preparedStatement.setDate(7, vDateConverted);

			preparedStatement.executeUpdate();
			preparedStatement.close();

			logger.debug("Update query successfully executed");
			logger.debug("END");

		} catch (Throwable t) {
			logger.error("An unexpected error occured while updating hierarchy for backup");
			throw new SpagoBIServiceException("An unexpected error occured while updating hierarchy for backup", t);
		}

		if ((Boolean) paramsMap.get("doPropagation")) {

			String hierNameTargetColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.HIER_NM_T, dataSource);
			String hierNameSourceColumn = AbstractJDBCDataset.encapsulateColumnName(HierarchyConstants.HIER_NM_M, dataSource);

			String updateRelQuery = "UPDATE " + HierarchyConstants.REL_MASTER_TECH_TABLE_NAME + " SET " + hierNameTargetColumn + "= ?, " + bkpColumn + " = ?, "
					+ bkpTimestampColumn + "= ? WHERE " + hierNameTargetColumn + "= ? AND " + hierNameSourceColumn + "= ?";

			logger.debug("The relations update query is [" + updateRelQuery + "]");

			try (Statement stmt = databaseConnection.createStatement();
					PreparedStatement preparedRelStatement = databaseConnection.prepareStatement(updateRelQuery)) {
				preparedRelStatement.setString(1, (String) paramsMap.get("hierTargetName") + "_" + timestamp);
				preparedRelStatement.setBoolean(2, true);
				preparedRelStatement.setTimestamp(3, new java.sql.Timestamp(timestamp));
				preparedRelStatement.setString(4, (String) paramsMap.get("hierTargetName"));
				preparedRelStatement.setString(5, (String) paramsMap.get("hierSourceName"));

				preparedRelStatement.executeUpdate();
				preparedRelStatement.close();

				logger.debug("Update relations query successfully executed");
				logger.debug("END");

			} catch (Throwable t) {
				logger.error("An unexpected error occured while updating hierarchy relations for backup");
				throw new SpagoBIServiceException("An unexpected error occured while updating hierarchy relations for backup", t);
			}
		}

	}
}
