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
            String noButtonTooltip,
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
        dialogPane.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);

        Button dontClearButton = (Button) dialogPane.lookupButton(ButtonType.NO);
        dontClearButton.setText(buttonNoText);
        dontClearButton.disableProperty().bind(checkBox.selectedProperty().not());
        dontClearButton.setStyle("-fx-base: #5b0000; -fx-text-fill: #ffd6d6");
        dontClearButton.setTooltip(new Tooltip(noButtonTooltip));

        Button clearButton = (Button) dialogPane.lookupButton(ButtonType.YES);
        clearButton.setText(buttonYesText);
        clearButton.disableProperty().bind(checkBox.selectedProperty().not());
        clearButton.setStyle("-fx-base: #ce6700; -fx-text-fill: white");

        Button cancelButton = (Button) dialogPane.lookupButton(ButtonType.CANCEL);
        cancelButton.setStyle("-fx-base: green");
    }
}