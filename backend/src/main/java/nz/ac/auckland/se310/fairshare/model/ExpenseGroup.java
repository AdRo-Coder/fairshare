package nz.ac.auckland.se310.fairshare.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name="expense_group")
public class ExpenseGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "group_name", nullable = false, length = 50)
    private String groupName;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "base_currency", nullable = false, length = 3)
    private User.Currency baseCurrency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserInGroup> members = new HashSet<>();

    protected ExpenseGroup() {} // JPA

    public ExpenseGroup(String groupName, String description, User.Currency baseCurrency, User createdBy) {
        this.groupName = groupName;
        this.description = description;
        this.baseCurrency = baseCurrency;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        addMember(createdBy); // AC1: the creator is always a member
    }

    public void addMember(User user) {
        members.add(new UserInGroup(this, user));
    }

    public void removeMember(User user) {
        if (user.getId().equals(createdBy.getId())) {
            throw new IllegalStateException("The group creator cannot leave the group");
        }
        members.removeIf(m -> m.getUser().getId().equals(user.getId()));
    }

    public Long getId() { return id; }
    public String getGroupName() { return groupName; }
    public String getDescription() { return description; }
    public User.Currency getBaseCurrency() { return baseCurrency; }
    public Instant getCreatedAt() { return createdAt; }
    public User getCreatedBy() { return createdBy; }

    public Set<UserInGroup> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpenseGroup other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
