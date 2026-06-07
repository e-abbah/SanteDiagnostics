/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package santediagnosticsltd;

/**
 *
 * @author Emmanuel Abbah
 */


public class Session {
    private static int userId;
    private static String userName;
    private static String userRole;
    private static String userEmail;

    public static void login(int id, String name, String role, String email) {
        userId = id;
        userName = name;
        userRole = role;
        userEmail = email;
    }

    public static void logout() {
        userId = 0;
        userName = null;
        userRole = null;
        userEmail = null;
    }

    public static int getUserId()     { return userId; }
    public static String getUserName()  { return userName; }
    public static String getUserRole()  { return userRole; }
    public static String getUserEmail() { return userEmail; }
}
