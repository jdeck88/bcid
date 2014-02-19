package bcid;

import ezid.EZIDService;
import util.SettingsManager;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

/**
 * Mint new expeditions.  Includes the automatic creation of a core set of entity types
 */
public class expeditionMinter {
    protected Connection conn;
    public ArrayList<Integer> expeditionResources;
    private SettingsManager sm;
    private EZIDService ezidAccount;


    /**
     * The constructor defines the class-level variables used when minting Expeditions.
     * It defines a generic set of entities (process, information content, objects, agents)
     * that can be used for any expedition.
     *
     * @throws Exception
     */
    public expeditionMinter() throws Exception {
        database db = new database();
        conn = db.getConn();

        // Initialize settings manager
        sm = SettingsManager.getInstance();
        try {
            sm.loadProperties();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * mint Expedition
     *
     * @param expedition_code
     * @param expedition_title
     * @param strAbstract
     * @param users_id
     * @return
     */
    public Integer mint(
            String expedition_code,
            String expedition_title,
            String strAbstract,
            Integer users_id,
            Integer project_id) throws Exception {

        Integer expedition_id = null;

        if (!userExistsInProject(users_id, project_id)) {
            throw new Exception("User ID " + users_id + " is not authorized to create expeditions in this project");
        }

        /**
         *  Insert the values into the expeditions table
         */
        try {
            try {
                checkExpeditionCodeValid(expedition_code);
                checkExpeditionCodeAvailable(expedition_code, project_id);
            } catch (Exception e) {
                throw new Exception(e.getMessage(), e);
            }

            // Generate an internal ID to track this submission
            UUID internalID = UUID.randomUUID();

            // Use auto increment in database to assign the actual identifier.. this is threadsafe this way
            String insertString = "INSERT INTO expeditions " +
                    "(internalID, expedition_code, expedition_title, abstract, users_id, project_id) " +
                    "values (?,?,?,?,?,?)";

            PreparedStatement insertStatement = null;
            insertStatement = conn.prepareStatement(insertString);
            insertStatement.setString(1, internalID.toString());
            insertStatement.setString(2, expedition_code);
            insertStatement.setString(3, expedition_title);
            insertStatement.setString(4, strAbstract);
            insertStatement.setInt(5, users_id);
            insertStatement.setInt(6, project_id);
            insertStatement.execute();

            // Get the datasets_id that was assigned
            expedition_id = getExpeditionIdentifier(internalID);
        } catch (SQLException e) {
            //e.printStackTrace();
            throw new Exception(e.getMessage());
        }

        return expedition_id;
    }


    /**
     * Attach an individual URI reference to a expedition
     *
     * @param expedition_code
     * @param bcid
     * @throws Exception
     */
    public void attachReferenceToExpedition(String expedition_code, String bcid, Integer project_id) throws Exception {
        Integer expedition_id = getExpeditionIdentifier(expedition_code, project_id);
        Integer datasetsId = new resolver(bcid).getDataGroupID();

        String insertString = "INSERT INTO expeditionsBCIDs " +
                "(expedition_id, datasets_id) " +
                "values (?,?)";

        PreparedStatement insertStatement = null;
        insertStatement = conn.prepareStatement(insertString);
        insertStatement.setInt(1, expedition_id);
        insertStatement.setInt(2, datasetsId);
        insertStatement.execute();
    }

    /**
     * Return the expedition identifier given the internalID
     *
     * @param datasetUUID
     * @return
     * @throws java.sql.SQLException
     */
    private Integer getExpeditionIdentifier(UUID datasetUUID) throws SQLException {
        Statement stmt = conn.createStatement();
        String sql = "select expedition_id from expeditions where internalID = '" + datasetUUID.toString() + "'";
        ResultSet rs = stmt.executeQuery(sql);
        try {
            rs.next();
            return rs.getInt("expedition_id");
        } catch (SQLException e) {
            return null;
        }
    }

    private Integer getExpeditionIdentifier(String expedition_code, Integer project_id) throws SQLException {
        Statement stmt = conn.createStatement();
        String sql = "SELECT expedition_id " +
                "FROM expeditions " +
                "WHERE expedition_code = '" + expedition_code + "' AND " +
                "project_id = " + project_id;
        ResultSet rs = stmt.executeQuery(sql);
        try {
            rs.next();
            return rs.getInt("expedition_id");
        } catch (SQLException e) {
            return null;
        }
    }

    public Boolean expeditionExistsInProject(String expedition_code, Integer ProjectId) throws SQLException {
        Statement stmt = conn.createStatement();
        String sql = "select expedition_id from expeditions " +
                "where expedition_code = '" + expedition_code + "' && " +
                "project_id = " + ProjectId;
        ResultSet rs = stmt.executeQuery(sql);
        try {
            if (rs.next()) return true;
        } catch (SQLException e) {
            return false;
        }
        return false;
    }

    public String printMetadata(int id) throws SQLException {
        StringBuilder sb = new StringBuilder();
        Statement stmt = conn.createStatement();
        String sql = "select expedition_id,expedition_code,expedition_title,username from expeditions,users where users.user_id = expeditions.users_id && expedition_id =" + id;
        ResultSet rs = stmt.executeQuery(sql);
        sb.append("***expedition***");

        // Get result set meta data
        ResultSetMetaData rsmd = rs.getMetaData();
        int numColumns = rsmd.getColumnCount();

        while (rs.next()) {
            // Loop mapped values, now we know the type
            for (int i = 1; i <= numColumns; i++) {
                String val = rsmd.getColumnLabel(i);
                sb.append("\n" + val + " = " + rs.getString(val));
            }
        }
        return sb.toString();
    }

    public String printMetadataHTML(int id) throws SQLException {
        StringBuilder sb = new StringBuilder();
        Statement stmt = conn.createStatement();
        String sql = "SELECT expedition_id,expedition_code,expedition_title,username " +
                "FROM expeditions,users " +
                "WHERE users.user_id = expeditions.users_id " +
                "&& expedition_id = " + id;
        ResultSet rs = stmt.executeQuery(sql);
        sb.append("<table>");

        // Get result set meta data
        ResultSetMetaData rsmd = rs.getMetaData();
        int numColumns = rsmd.getColumnCount();

        while (rs.next()) {
            // Loop mapped values, now we know the type
            for (int i = 1; i <= numColumns; i++) {
                String val = rsmd.getColumnLabel(i);
                sb.append("<tr><td>" + val + "</td><td>" + rs.getString(val) + "</td></tr>");
            }
        }
        sb.append("</table>");
        return sb.toString();
    }


    /**
     * Discover if a user owns this expedition or not
     * @param users_id
     * @param expedition_code
     * @return
     * @throws SQLException
     */
    public boolean userOwnsExpedition(Integer users_id, String expedition_code, Integer project_id) throws SQLException {
        Statement stmt = conn.createStatement();
        //String sql = "select expedition_id,expedition_code,expedition_title,username from expeditions,users where users.user_id = expeditions.users_id && users.username =\"" + remoteUser + "\"";

        String sql = "SELECT " +
                "   count(*) as count " +
                "FROM " +
                "   expeditions " +
                "WHERE " +
                "   expedition_code='" + expedition_code + "' && " +
                "   users_id = " + users_id + " && " +
                "   project_id = " + project_id;

        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        if (rs.getInt("count") < 1)
            return false;
        else
            return true;
    }

    /**
     * Discover if a user belongs to an project
     * @param users_id
     * @param project_id
     * @return
     * @throws SQLException
     */
    public boolean userExistsInProject(Integer users_id, Integer project_id) throws SQLException {
        String selectString = "SELECT count(*) as count FROM usersProjects WHERE users_id = ? && project_id = ?";

        PreparedStatement stmt = conn.prepareStatement(selectString);

        stmt.setInt(1, users_id);
        stmt.setInt(2, project_id);

        ResultSet rs = stmt.executeQuery();
        rs.next();
        return rs.getInt("count") >= 1;
    }

    /**
     * Generate a Deep Links Format data file for describing a set of root prefixes and associated concepts
     *
     * @param expedition_code
     * @return
     * @throws java.sql.SQLException
     */
    public String getDeepRoots(String expedition_code, Integer project_id) throws SQLException {
        // Get todays's date
        DateFormat dateFormat;
        dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();
        String expedition_title = null;

        StringBuilder sb = new StringBuilder();

        // Construct the query
        Statement stmt = conn.createStatement();
        String sql =
                "SELECT " +
                        " d.prefix as BCID, " +
                        " d.resourceType as resourceType," +
                        " a.expedition_title as expedition_title " +
                        "FROM " +
                        " expeditions a, expeditionsBCIDs b, datasets d " +
                        "WHERE" +
                        " a.expedition_id = b.expedition_id && " +
                        " b.datasets_id = d.datasets_id && \n" +
                        " a.expedition_code = '" + expedition_code + "' && \n" +
                        " a.project_id = " + project_id;

        // Write the concept/prefix elements section
        sb.append("[\n{\n\t\"data\": [\n");
        ResultSet rs = stmt.executeQuery(sql);
        while (rs.next()) {
            // Grap the expedition_title in the query
            if (expedition_title == null & !rs.getString("expedition_title").equals(""))
                expedition_title = rs.getString("expedition_title");

            // Grap the prefixes and concepts associated with this
            sb.append("\t\t{\n");
            sb.append("\t\t\t\"prefix\":\"" + rs.getString("BCID") + "\",\n");
            sb.append("\t\t\t\"concept\":\"" + rs.getString("resourceType") + "\",\n");
            sb.append("\t\t\t\"alias\":\"" + rs.getString("expedition_title") + "\"\n");
            sb.append("\t\t}");
            if (!rs.isLast())
                sb.append(",");

            sb.append("\n");
        }
        sb.append("\t]\n},\n");

        // Write the metadata section
        sb.append("{\n");
        sb.append("\t\"metadata\": {\n");
        sb.append("\t\t\"name\": \" " + expedition_code + "\",\n");
        if (expedition_title != null)
            sb.append("\t\t\"description\": \"" + expedition_title + "\",\n");
        sb.append("\t\t\"date\": \" " + dateFormat.format(date) + "\"\n");
        sb.append("\t}\n");
        sb.append("}\n");
        sb.append("]\n");
        return sb.toString();
    }

    public String expeditionTable(String remoteUser) throws SQLException {

        StringBuilder sb = new StringBuilder();
        Statement stmt = conn.createStatement();
        //String sql = "select expedition_id,expedition_code,expedition_title,username from expeditions,users where users.user_id = expeditions.users_id && users.username =\"" + remoteUser + "\"";

        String sql = "SELECT " +
                "   a.expedition_id as expedition_id," +
                "   a.expedition_code as expedition_code," +
                "   a.expedition_title as expedition_title," +
                "   d.prefix as BCID," +
                "   d.resourceType as resourceType " +
                "FROM " +
                "   expeditions a,expeditionsBCIDs b,datasets d,users u " +
                "WHERE " +
                "   a.expedition_id=b.expedition_id && " +
                "   b.datasets_id=d.datasets_id && " +
                "   a.users_id = u.user_id && " +
                "   u.username=\"" + remoteUser + "\"";

        ResultSet rs = stmt.executeQuery(sql);

        // Get result set meta data

        sb.append("<table>\n");
        sb.append("\t<tr>\n");
        sb.append("\t\t<td><b>Expedition Details</b></td>\n");
        sb.append("\t\t<td><b>Expedition BCIDs</b></td>\n");
        sb.append("\t</tr>\n");

        Integer expedition_id = 0;
        Integer thisExpedition_id = 0;
        int count = 0;
        while (rs.next()) {

            thisExpedition_id = rs.getInt("expedition_id");

            // Structure the first column-- expeditions
            if (thisExpedition_id != expedition_id) {
                if (count > 0) {
                    sb.append("\t\t\t</table>\n\t\t</td>\n");
                    sb.append("\t</tr>\n");
                }

                sb.append("\t<tr>\n");
                sb.append("\t\t<td valign=top>\n");
                sb.append("\t\t\t<table><tr><td>expeditionID " + rs.getString("expedition_id") + "</td></tr>" +
                        "<tr><td>" + rs.getString("expedition_code") + "</td></tr>" +
                        "<tr><td>" + rs.getString("expedition_title") + "</td></tr></table>\n");
                sb.append("\t\t</td>\n");

                sb.append("\t\t<td valign=top>\n\t\t\t<table>\n");
            } else {
                //sb.append("\n\t\t<td></td>\n");
            }

            // Structure the second column-- BCIDs associated with expeditions
            ResourceTypes rt = new ResourceTypes();
            String rtString;
            try {
                rtString = "<a href='" + rs.getString("resourceType") + "'>" + rt.get(rs.getString("resourceType")).string + "</a>";
            } catch (Exception e) {
                rtString = "<a href='" + rs.getString("resourceType") + "'>" + rs.getString("resourceType") + "</a>";
            }

            sb.append("\t\t\t\t<tr><td><a href='http://biscicol.org/id/" + rs.getString("BCID") + "'>" +
                    rs.getString("BCID") + "</a></td>" +
                    "<td>is_a</td><td>" +
                    rtString +
                    "</td></tr>\n");

            // Close the BCID section tag
            if (thisExpedition_id != expedition_id) {
                //if (count > 0) {
                //    sb.append("\n\t\t\t</table>");
                //    sb.append("\n\t\t</td>");
                //}
                expedition_id = thisExpedition_id;
            }
            count++;
            if (rs.isLast())
                sb.append("\t\t\t</table>\n\t\t</td>\n");
        }

        sb.append("\t</tr>\n</table>\n");

        return sb.toString();
    }


    public static void main(String args[]) {
        try {
            // See if the user owns this expedition or no
            expeditionMinter expedition = new expeditionMinter();
            //    System.out.println("validation XML for project = " +expedition.getValidationXML(1));
           /*
            if (expedition.expeditionExistsInProject("DEMOH", 1)) {
                System.out.println("expedition exists in project");
            } else {
                System.out.println("expedition does not exist in project");
            }
            */
            /*System.out.println(expedition.getDeepRoots("HDIM"));

            if (expedition.userOwnsExpedition(8, "DEMOG")) {
                System.out.println("YES the user owns this expedition");
            } else {
                System.out.println("NO the user does not own this expedition");
            }

*/
            // System.out.println(expedition.getLatestGraphsByExpedition(1));
            // Test associating a BCID to a expedition
            /*
            expedition.attachReferenceToExpedition("DEMOH", "ark:/21547/Fu2");
            */

            // Test creating a expedition

            Integer expedition_id = expedition.mint(
                    "DEMOH",
                    "Test creating expedition under an project for which it already exists",
                    null,
                    8, 4);

            System.out.println(expedition.printMetadata(expedition_id));

            //System.out.println(p.expeditionTable("demo"));

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Check that expedition code is between 4 and 6 characters
     *
     * @param expedition_code
     * @return
     */
    private void checkExpeditionCodeValid(String expedition_code) throws Exception {
        // Check expedition_code length
        if (expedition_code.length() < 4 || expedition_code.length() > 6)
            throw new Exception("Expedition code " + expedition_code + " must be between 4 and 6 characters long");
        // Check to make sure characters are normal!
        if (!expedition_code.matches("[a-zA-Z0-9]*")) {
            throw new Exception("Expedition code " + expedition_code + " contains invalid characters.");
        }
    }

    /**
     * Check that expedition code is no already in the database
     *
     * @param expedition_code
     * @return
     */
    private void checkExpeditionCodeAvailable(String expedition_code, Integer project_id) throws Exception {

        Statement stmt = conn.createStatement();
        String sql = "SELECT count(*) as count " +
                "FROM expeditions " +
                "WHERE expedition_code = '" + expedition_code + "' AND " +
                "project_id = " + project_id;
        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        Integer count = rs.getInt("count");
        if (count >= 1) {
            throw new Exception("Expedition code " + expedition_code + " already exists for this project.");
        }

    }
}
