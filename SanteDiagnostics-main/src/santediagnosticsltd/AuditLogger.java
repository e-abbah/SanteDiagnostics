/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package santediagnosticsltd;

/**
 *
 * @author Emmanuel Abbah
 */
import java.sql.Connection;
import java.sql.PreparedStatement;

public class AuditLogger {

    public static void log(int userId, String action, String affectedTable, int affectedRecordId) {
        String sql = "INSERT INTO audit_log (user_id, action, affected_table, affected_record_id) VALUES (?, ?, ?, ?)";
        try {
            Connection conn = DBConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, userId);
            ps.setString(2, action);
            ps.setString(3, affectedTable);
            ps.setInt(4, affectedRecordId);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("Audit log failed: " + e.getMessage());
        }
    }
}