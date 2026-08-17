package nz.ac.auckland.se310.fairshare.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "user_in_group",
        uniqueConstraints =
        @UniqueConstraint(name = "uq_uig_user_group", columnNames = {"user_id", "group_id"}))
public class UserInGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_in_group_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ExpenseGroup group;

    protected UserInGroup() {} // JPA

    UserInGroup(ExpenseGroup group, User user) {
        this.group = group;
        this.user = user;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public ExpenseGroup getGroup() { return group; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof UserInGroup other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}