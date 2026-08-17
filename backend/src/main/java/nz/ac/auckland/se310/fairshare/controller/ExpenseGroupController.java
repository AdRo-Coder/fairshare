package nz.ac.auckland.se310.fairshare.controller;

import jakarta.validation.Valid;
import nz.ac.auckland.se310.fairshare.dto.CreateGroupRequest;
import nz.ac.auckland.se310.fairshare.dto.GroupResponse;
import nz.ac.auckland.se310.fairshare.security.CurrentUserProvider;
import nz.ac.auckland.se310.fairshare.service.ExpenseGroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/groups")
public class ExpenseGroupController {

    private final ExpenseGroupService groupService;
    private final CurrentUserProvider currentUser;

    public ExpenseGroupController(ExpenseGroupService groupService, CurrentUserProvider currentUser) {
        this.groupService = groupService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody CreateGroupRequest request) {
        GroupResponse created = groupService.createGroup(request, currentUser.currentUserId());
        return ResponseEntity
                .created(URI.create("/groups/" + created.id()))
                .body(created);
    }

    @GetMapping
    public List<GroupResponse> list() {
        return groupService.getGroupsForUser(currentUser.currentUserId());
    }

    @GetMapping("/{id}")
    public GroupResponse get(@PathVariable Long id) {
        return groupService.getGroup(id, currentUser.currentUserId());
    }
}