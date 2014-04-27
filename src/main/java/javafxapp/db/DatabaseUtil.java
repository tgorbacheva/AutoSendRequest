package javafxapp.db;

import javafxapp.adapter.domain.Adapter;
import javafxapp.adapter.domain.AdapterDetails;
import javafxapp.adapter.domain.Settings;
import javafxapp.controller.SettingsController;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: vmaksimov
 */
public class DatabaseUtil {

    private static final String adapter_query = "SELECT ID, numReq, requestXml, id210fz, smevAddress, adapterName, responseStatus FROM adapter NATURAL JOIN adapterDetails";
    private static Connection connection;
    private static Statement statement;

    public static void createDB() {
        if (connection == null) {
            try {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:autosend.db");
            } catch (Exception e) {
                System.out.println("Ошибка в создании нового соединения " + e.getLocalizedMessage());
            }
            String query = "CREATE TABLE adapter (ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "id210fz VARCHAR(32), " +
                    "numReq INTEGER , " +
                    "requestXml VARCHAR(32), " +
                    "responseXml VARCHAR(32), " +
                    "responseStatus VARCHAR(32)," +
                    "dateCall VARCHAR(32),"+
                    "FOREIGN KEY(id210fz) REFERENCES adapterDetails(id210fz));" +

                    "CREATE TABLE adapterDetails (id210fz VARCHAR(32) PRIMARY KEY, " +
                    "smevAddress VARCHAR(32), " +
                    "foiv VARCHAR(32), " +
                    "adapterName VARCHAR(32));" +

                    "CREATE TABLE smevfield (ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name VARCHAR(32), " +
                    "value VARCHAR(32), " +
                    "foiv VARCHAR(32));" +

                    "CREATE TABLE settings (ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "pathFile VARCHAR(32), " +
                    "key_alias VARCHAR(32), " +
                    "cert_alias VARCHAR(32)," +
                    "password VARCHAR(32));" +

                    "INSERT INTO adapterDetails (id210fz, smevAddress, foiv, adapterName) VALUES ('07', 'http://192.168.100.96:7777/gateway/services/SID0003245', 'ФНС','(Сведения из ЕГРИП)');" +
                    "INSERT INTO adapterDetails (id210fz, smevAddress, foiv, adapterName) VALUES ('07_2', 'http://192.168.100.96:7777/gateway/services/SID0003245', 'ФНС','(Сведения из ЕГРЮЛ)');" +

                    "INSERT INTO settings (pathFile, key_alias, cert_alias, password) VALUES ('', 'RaUser-2908cdc2-4aff-47c6-9636-d2a98ba3d2b5','RaUser-2908cdc2-4aff-47c6-9636-d2a98ba3d2b5','1234567890');";
            executeUpdate(query);
        }
    }

    public static void insertRequests(List<Adapter> adapters) {
        clearTable("ADAPTER");

        String query = "", id210fz = "";
        for (Adapter adapter: adapters) {
            if (adapter.getDeclarant() != null && adapter.getDeclarant().equals("ip"))
                id210fz = "07";
            else if (adapter.getDeclarant() != null && adapter.getDeclarant().equals("ul"))
                id210fz = "07_2";
            query += "INSERT INTO adapter (id210fz, numReq, requestXml, responseStatus) VALUES ('"+ id210fz +"', " + adapter.getNumReq() + ", '" + adapter.getRequestXml() + "', '');";
        }
        executeUpdate(query);
    }

    public static void savePathFile(String path) {
        String query = "UPDATE settings SET pathFile='" + path + "';";
        executeUpdate(query);
    }

    public static void saveSmevFields(String foiv, HashMap<String, String> smevFileds) {
        clearTable("smevfield");
        String query = "";
        for (Map.Entry<String, String> entry : smevFileds.entrySet()) {
            query += "INSERT INTO smevfield (name, value, foiv) VALUES ('" + entry.getKey() + "', '" + entry.getValue() + "', '" + foiv + "');";

        }
        executeUpdate(query);
    }

    public static Settings getSettings() {
        Settings settings = new Settings();
        String query = "SELECT pathFile, key_alias, cert_alias, password FROM settings";
        ResultSet resultSet = executeQuery(query);
        try {
            while (resultSet.next()) {
                settings.setPathFile(resultSet.getString(1));
                settings.setKeyAlias(resultSet.getString(2));
                settings.setCertAlias(resultSet.getString(3));
                settings.setPassword(resultSet.getString(4));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return settings;
    }

    public static HashMap<String, String> getSmevFields(String foiv) {
        HashMap<String, String> hashMap = new HashMap<>();
            String query = "SELECT name, value FROM smevfield WHERE foiv = '" + foiv + "'";
            ResultSet resultSet = executeQuery(query);
        try {
            while (resultSet.next()) {
                hashMap.put(resultSet.getString("name"), resultSet.getString("value"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return hashMap;
    }

    private static void clearTable(String nameTable) {
        String dropQuery = "delete from " + nameTable;
        executeUpdate(dropQuery);
    }

    public static void close() {
        try {
            statement = connection.createStatement();
            statement.execute("SHUTDOWN");
            statement.close();
        } catch (SQLException e) {
            System.out.println("Ошибка в закрытии соединения " + e.getMessage());
        }

    }

    public static List<Adapter> getRequest(String id210fz) {
        List<Adapter> adapters = new ArrayList<>();
        String query = adapter_query + " WHERE id210fz = '" + id210fz + "'";
        getAdapter(adapters, query);
        return adapters;
    }

    public static void saveResponse(Adapter adapter) {
        String query = "UPDATE adapter SET responseXml = '" + adapter.getResponseXml() + "', " +
                    "responseStatus = '" + adapter.getResponseStatus() + "', dateCall = '" + new java.util.Date() + "'" +
                    "WHERE numReq = '" + adapter.getNumReq() + "' and id210fz = '" + adapter.getId210fz() + "';";
        executeUpdate(query);
    }

    public static void saveResponseById(Adapter adapter) {
        String query = "UPDATE adapter SET responseXml = '" + adapter.getResponseXml() + "', " +
                        "responseStatus = '" + adapter.getResponseStatus() + "', dateCall = '" + new java.util.Date() + "'" +
                        "WHERE id = '" + adapter.getId() + "';";
        executeUpdate(query);
    }

    public static List<Adapter> findReqReadyToSend(int count) {
        List<Adapter> adapterList = new ArrayList<>();
        String query = adapter_query + " WHERE responseStatus NOT LIKE 'ACCEPT' limit "+ count +";";
        getAdapter(adapterList, query);
        return adapterList;
    }

    private static void buildAdapter(List<Adapter> adapterList, ResultSet resultSet) throws SQLException {
        Adapter adapter = new Adapter();
        adapter.setId(resultSet.getInt("ID"));
        adapter.setNumReq(resultSet.getInt("numReq"));
        adapter.setId210fz(resultSet.getString("id210fz"));
        adapter.setRequestXml(resultSet.getString("requestXml"));
        AdapterDetails adapterDetails = new AdapterDetails();
        adapterDetails.setSmevAddress(resultSet.getString("smevAddress"));
        adapterDetails.setAdapterName(resultSet.getString("adapterName"));
        adapter.setResponseStatus(resultSet.getString("responseStatus"));
        adapter.setAdapterDetails(adapterDetails);
        adapterList.add(adapter);
    }

    public static void saveSettings() {
        String query = "UPDATE settings SET key_alias='" + SettingsController.keyAlias.getText() + "', " +
                "key_alias='" + SettingsController.certAlias.getText() + "', " +
                "password ='" + SettingsController.password.getText() + "';";
        executeUpdate(query);
    }

    public static void saveAddressService(String address, String id210fz) {
        String query = "UPDATE adapterDetails SET  smevAddress ='" + address + "' WHERE id210fz in ('" + id210fz + "','" + id210fz + "_2');";
        executeUpdate(query);
    }

    public static List<AdapterDetails> getAdapterDetails() {
        List<AdapterDetails> listAdapterDetails = new ArrayList<>();
            String query = "SELECT smevAddress FROM adapterDetails";
            ResultSet resultSet = executeQuery(query);
            AdapterDetails adapterDetails;
        try {
            while (resultSet.next()) {
                adapterDetails = new AdapterDetails();
                adapterDetails.setSmevAddress(resultSet.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return listAdapterDetails;
    }

    public static List<Adapter> getResponseStatus(List<Adapter> adapters) {
        List<Adapter> adapterList = new ArrayList<>();
        String in = "";
        for (Adapter adapter: adapters){
            in = in + adapter.getId() + ",";
        }
        in = in.substring(0, in.length()-1);
        String query = adapter_query + " WHERE ID in ("+ in +");";
        getAdapter(adapterList, query);
        return adapterList;
    }

    public static void getAdapter(List<Adapter> adapterList, String query) {
        try {
            statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(query);
            while (resultSet.next()) {
                buildAdapter(adapterList, resultSet);
            }
            statement.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public static ResultSet executeQuery(String query) {
        ResultSet resultSet = null;
        try {
            statement = connection.createStatement();
            resultSet = statement.executeQuery(query);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return resultSet;
    }

    public static void executeUpdate(String query) {
        try {
            statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}
