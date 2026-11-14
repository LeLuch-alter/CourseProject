package kz.kvit.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import kz.kvit.i18n.I18n;
import kz.kvit.model.Loan;
import kz.kvit.model.LoanEntry;
import kz.kvit.model.LoanStatus;
import kz.kvit.service.LoanService;

import java.util.List;

/**
 * Loans between friends ("расписки"): pending requests, active and closed loans.
 * Requests are created on the web site; here the other party can confirm or decline them,
 * and the initiator can withdraw a request that was not answered yet.
 */
public class LoansView implements Page {
    private final LoanService loanService;
    private final ModalHost host;
    private final VBox content = new VBox(20);
    private final VBox root;

    public LoansView(LoanService loanService, ModalHost host) {
        this.loanService = loanService;
        this.host = host;
        Button refresh = new Button(I18n.t("common.refresh"), Icons.create(Icons.REFRESH, 13));
        refresh.setOnAction(event -> refresh());
        Ui.tooltip(refresh, "F5");
        root = Ui.page(I18n.t("loans.eyebrow"), I18n.t("loans.title"), refresh);
        root.getChildren().addAll(Ui.wrapped(I18n.t("loans.hint"), "helper"), content);
        load();
    }

    @Override
    public Node getRoot() {
        return root;
    }

    @Override
    public void refresh() {
        load();
    }

    private void load() {
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(36, 36);
        content.getChildren().setAll(progress);
        BackgroundTask.run(loanService::getLoans, this::show,
                message -> {
                    InfoBar error = new InfoBar();
                    error.show(InfoBar.Severity.ERROR, I18n.t("common.loadFailed", message));
                    Button retry = new Button(I18n.t("common.retry"), Icons.create(Icons.REFRESH, 13));
                    retry.setOnAction(event -> load());
                    content.getChildren().setAll(new VBox(12, error, retry));
                });
    }

    private void show(List<LoanEntry> loans) {
        if (loans.isEmpty()) {
            VBox empty = new VBox(Ui.emptyState(Icons.DOCUMENT, I18n.t("loans.empty.title"),
                    I18n.t("loans.empty.text"), null));
            empty.getStyleClass().add("glass-panel");
            empty.setPadding(new Insets(56, 32, 56, 32));
            content.getChildren().setAll(empty);
            return;
        }
        List<LoanEntry> pending = loans.stream().filter(e -> e.loan().status() == LoanStatus.PENDING).toList();
        List<LoanEntry> active = loans.stream().filter(e -> e.loan().status() == LoanStatus.ACTIVE).toList();
        List<LoanEntry> closed = loans.stream().filter(e -> e.loan().status().isClosed()).toList();

        content.getChildren().clear();
        if (!pending.isEmpty()) {
            content.getChildren().add(section(I18n.t("loans.section.pending"), pending));
        }
        if (!active.isEmpty()) {
            content.getChildren().add(section(I18n.t("loans.section.active"), active));
        }
        if (!closed.isEmpty()) {
            content.getChildren().add(section(I18n.t("loans.section.closed"), closed));
        }
        Motion.fadeIn(content, 0);
    }

    private Node section(String title, List<LoanEntry> loans) {
        HBox header = new HBox(10, Ui.label(title, "heading-sm"), Ui.label(String.valueOf(loans.size()), "badge"));
        header.setAlignment(Pos.CENTER_LEFT);
        VBox rows = new VBox();
        for (LoanEntry entry : loans) {
            rows.getChildren().add(row(entry));
        }
        VBox panel = new VBox(10, header, rows);
        panel.getStyleClass().add("glass-panel");
        panel.setPadding(new Insets(22, 24, 18, 24));
        return panel;
    }

    private Node row(LoanEntry entry) {
        Loan loan = entry.loan();
        String role = I18n.t(entry.iAmLender() ? "loans.role.lender" : "loans.role.borrower", entry.otherName());
        String second = role + " · " + I18n.t("loans.due", Formats.date(loan.dueDate()));
        if (loan.receiptNo() != null) {
            second += " · " + loan.receiptNo();
        }
        VBox text = new VBox(2, Ui.label(loan.purpose(), "ledger-title"), Ui.wrapped(second, "helper"));
        HBox.setHgrow(text, Priority.ALWAYS);

        Label amount = Ui.label((entry.iAmLender() ? "+" : "−") + Formats.money(loan.amount()), "ledger-amount");
        VBox right = new VBox(4, amount, statusChip(entry));
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setMinWidth(Label.USE_PREF_SIZE);

        HBox top = new HBox(12, Icons.create(Icons.DOCUMENT, 14), text, right);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(10, top);
        if (loan.status() == LoanStatus.PENDING) {
            box.getChildren().add(pendingActions(entry));
        }
        box.getStyleClass().add("ledger-row");
        box.setPadding(new Insets(10, 0, 10, 0));
        return box;
    }

    private Label statusChip(LoanEntry entry) {
        LoanStatus status = entry.loan().status();
        if (entry.waitingForMe()) {
            return Ui.statusChip(I18n.t("loans.waitingForYou"), "warn");
        }
        String tone = switch (status) {
            case PENDING -> "muted";
            case ACTIVE -> "active";
            case REPAID -> "success";
            case DECLINED, CANCELLED -> "muted";
        };
        return Ui.statusChip(status.toString(), tone);
    }

    private Node pendingActions(LoanEntry entry) {
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        if (entry.waitingForMe()) {
            Button accept = new Button(I18n.t("loans.accept"), Icons.create(Icons.CHECK, 12));
            accept.getStyleClass().add("cta");
            accept.setOnAction(event -> openAccept(entry));
            Button decline = new Button(I18n.t("loans.decline"));
            decline.setOnAction(event -> host.confirm(I18n.t("loans.decline.title"),
                    I18n.t("loans.decline.text", entry.otherName(), Formats.money(entry.loan().amount())),
                    I18n.t("loans.decline"), true,
                    () -> runAction(() -> loanService.decline(entry), "loans.toast.declined")));
            actions.getChildren().addAll(accept, decline);
        } else {
            Button withdraw = new Button(I18n.t("loans.withdraw"));
            withdraw.setOnAction(event -> host.confirm(I18n.t("loans.withdraw.title"),
                    I18n.t("loans.withdraw.text", entry.otherName()),
                    I18n.t("loans.withdraw"), true,
                    () -> runAction(() -> loanService.withdraw(entry), "loans.toast.withdrawn")));
            actions.getChildren().addAll(Ui.label(I18n.t("loans.waitingForOther", entry.otherName()), "helper"),
                    Ui.hSpacer(), withdraw);
        }
        return actions;
    }

    /** Confirmation dialog; asks for the card number when the current user is the borrower. */
    private void openAccept(LoanEntry entry) {
        Loan loan = entry.loan();
        TextField cardField = new TextField();
        cardField.setPromptText("0000 0000 0000 0000");
        InfoBar infoBar = new InfoBar();

        VBox body = new VBox(12, Ui.wrapped(I18n.t("loans.accept.text", entry.otherName(),
                Formats.money(loan.amount()), loan.purpose(), Formats.date(loan.dueDate())), "body-muted"));
        if (loan.interestRate() != null && loan.interestRate().signum() > 0) {
            body.getChildren().add(Ui.wrapped(I18n.t("loans.rate",
                    loan.interestRate().stripTrailingZeros().toPlainString()), "helper"));
        }
        if (entry.cardRequired()) {
            body.getChildren().addAll(Ui.field(I18n.t("loans.card"), cardField),
                    Ui.wrapped(I18n.t("loans.card.hint"), "helper"));
        }
        body.getChildren().add(infoBar);

        Button cancel = new Button(I18n.t("common.cancel"));
        cancel.setCancelButton(true);
        cancel.setOnAction(event -> host.closeModal());
        Button confirm = new Button(I18n.t("loans.accept"));
        confirm.getStyleClass().add("cta");
        confirm.setDefaultButton(true);
        confirm.setOnAction(event -> {
            confirm.setDisable(true);
            BackgroundTask.run(() -> {
                        loanService.accept(entry, cardField.getText());
                        return null;
                    },
                    result -> {
                        host.closeModal();
                        host.toast(I18n.t("loans.toast.accepted"), false);
                        load();
                    },
                    message -> {
                        confirm.setDisable(false);
                        infoBar.show(InfoBar.Severity.ERROR, message);
                    });
        });
        host.showDialog(ModalHost.dialogCard(I18n.t("loans.accept.title"), body, cancel, confirm), null);
        if (entry.cardRequired()) {
            Platform.runLater(cardField::requestFocus);
        }
    }

    private void runAction(Runnable action, String toastKey) {
        BackgroundTask.run(() -> {
                    action.run();
                    return null;
                },
                result -> {
                    host.toast(I18n.t(toastKey), false);
                    load();
                },
                message -> host.toast(message, true));
    }
}
