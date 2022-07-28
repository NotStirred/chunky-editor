package io.github.notstirred.chunkyeditor.ui.util;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class StyledSpecialApprovalConfirmationDialog extends Dialog<ButtonType> {
    private final CheckBox checkBox;

    public StyledSpecialApprovalConfirmationDialog(
            String title,
            String header,
            String content,
            String checkBoxLabel,
            String cancelButtonStyle,
            String okButtonStyle
    ) {
        setResultConverter(param -> param);
        checkBox = new CheckBox(checkBoxLabel);

        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStyleClass().add("alert");
        dialogPane.getStyleClass().add("warning");

        setTitle(title);
        dialogPane.setHeaderText(header);
        dialogPane.setContent(new VBox(16, new Text(content), checkBox));
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Node okButton = dialogPane.lookupButton(ButtonType.OK);
        okButton.setStyle(okButtonStyle);
        okButton.disableProperty().bind(checkBox.selectedProperty().not());

        Node cancelButton = dialogPane.lookupButton(ButtonType.CANCEL);
        cancelButton.setStyle(cancelButtonStyle);
    }
}