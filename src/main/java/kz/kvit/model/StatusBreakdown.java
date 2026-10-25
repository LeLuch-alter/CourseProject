package kz.kvit.model;

/** Number of debts per state. "active" does not include the overdue ones. */
public record StatusBreakdown(int active, int overdue, int paid, int cancelled) {

    public int total() {
        return active + overdue + paid + cancelled;
    }
}
