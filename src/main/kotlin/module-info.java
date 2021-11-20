module com.smx.yapt {
    requires javafx.fxml;
    requires kotlin.stdlib;

    requires org.controlsfx.controls;
    requires jsch;
    requires kotlin.stdlib.jdk7;
    requires afterburner.fx;
    requires kotlin.stdlib.jdk8;

    opens com.smx.yapt;
    exports com.smx.yapt;
}