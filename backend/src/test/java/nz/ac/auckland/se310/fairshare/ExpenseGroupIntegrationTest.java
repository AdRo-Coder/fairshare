package nz.ac.auckland.se310.fairshare;

import nz.ac.auckland.se310.fairshare.dto.CreateGroupRequest;
import nz.ac.auckland.se310.fairshare.dto.GroupResponse;
import nz.ac.auckland.se310.fairshare.exception.GroupNotFoundException;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.service.ExpenseGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestCurrentUserConfig.class)
class ExpenseGroupIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    @Autowired ExpenseGroupService groupService;
    @Autowired ExpenseGroupRepository groupRepository;
    @Autowired UserRepository userRepository;

    private Long aliceId;
    private Long bobId;

    @BeforeEach
    void setUp() {
        groupRepository.deleteAll();
        var users = userRepository.findAll();
        aliceId = users.get(0).getId();
        bobId = users.get(1).getId();
    }

    @Test
    void ac1_createsGroupWithCreatorAsMember() {
        GroupResponse created = groupService.createGroup(new CreateGroupRequest("Flat 3", null), aliceId);

        assertThat(created.id()).isNotNull();
        assertThat(created.memberCount()).isEqualTo(1);
        assertThat(groupRepository.findByIdAndMembersUserId(created.id(), aliceId)).isPresent();
    }

    @Test
    void ac5_overviewListsOnlyGroupsTheUserBelongsTo() {
        groupService.createGroup(new CreateGroupRequest("Alice one", null), aliceId);
        groupService.createGroup(new CreateGroupRequest("Alice two", null), aliceId);
        groupService.createGroup(new CreateGroupRequest("Bob one", null), bobId);

        assertThat(groupService.getGroupsForUser(aliceId))
                .hasSize(2)
                .extracting(GroupResponse::name)
                .containsExactlyInAnyOrder("Alice one", "Alice two");

        assertThat(groupService.getGroupsForUser(bobId)).hasSize(1);
    }

    @Test
    void ac6_duplicateNamesGetDistinctIdentifiers() {
        GroupResponse first = groupService.createGroup(new CreateGroupRequest("Trip", null), aliceId);
        GroupResponse second = groupService.createGroup(new CreateGroupRequest("Trip", null), aliceId);

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(groupService.getGroupsForUser(aliceId)).hasSize(2);
    }

    @Test
    void ac8_nonMemberCannotReadGroup() {
        GroupResponse created = groupService.createGroup(new CreateGroupRequest("Alice's flat", null), aliceId);

        Long groupId = created.id();

        assertThatThrownBy(() -> groupService.getGroup(groupId, bobId))
                .isInstanceOf(GroupNotFoundException.class);
    }
}
