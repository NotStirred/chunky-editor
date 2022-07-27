package io.github.notstirred.chunkyeditor.ui.util;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class ConfirmationDialogue extends Dialog<ButtonType> {
    final CheckBox checkBox;

    public ConfirmationDialogue(
            String title,
            String header,
            String content,
            String checkBoxLabel,
            String buttonNoText,
            String buttonYesText
    ) {
        this.setResultConverter(param -> param);
        checkBox = new CheckBox(checkBoxLabel);

        DialogPane dialogPane = this.getDialogPane();
        dialogPane.getStyleClass().add("alert");
        dialogPane.getStyleClass().add("warning");

        this.setTitle(title);
        dialogPane.setHeaderText(header);
        dialogPane.setContent(new VBox(16, new Text(content), checkBox));
        dialogPane.getButtonTypes().addAll(ButtonType.NO, ButtonType.CANCEL, ButtonType.YES);

        Button dontClearButton = (Button) dialogPane.lookupButton(ButtonType.NO);
        dontClearButton.setText(buttonNoText);
        dontClearButton.disableProperty().bind(checkBox.selectedProperty().not());
        dontClearButton.setStyle("-fx-base: red");
        Button clearButton = (Button) dialogPane.lookupButton(ButtonType.YES);
        clearButton.setText(buttonYesText);
        clearButton.setStyle("-fx-base: green");
        clearButton.disableProperty().bind(checkBox.selectedProperty().not());
    }
}