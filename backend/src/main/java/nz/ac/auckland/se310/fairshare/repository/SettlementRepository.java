package nz.ac.auckland.se310.fairshare.repository;

import nz.ac.auckland.se310.fairshare.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByGroupId(Long groupId);

    List<Settlement> findByGroupIdAndFromUserIdAndToUserIdOrderByIdDesc(Long groupId, Long fromUserId, Long toUserId);

    Optional<Settlement> findByGroupIdAndFromUserIdAndToUserId(Long groupId, Long fromUserId, Long toUserId);
}
