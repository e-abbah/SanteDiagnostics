package santediagnosticsltd;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SanteDiagnosticsLtd extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(
            getClass().getResource("/santediagnosticsltd/views/login.fxml")
        );
        Scene scene = new Scene(root);
        scene.getStylesheets().add(
            getClass().getResource("/santediagnosticsltd/css/styles.css").toExternalForm()
        );
        primaryStage.setTitle("Sante Diagnostics Ltd");
        primaryStage.setResizable(false);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        //System.out.println(org.mindrot.jbcrypt.BCrypt.hashpw("Admin@1234", org.mindrot.jbcrypt.BCrypt.gensalt()));
        launch(args);
    }
    
    
}