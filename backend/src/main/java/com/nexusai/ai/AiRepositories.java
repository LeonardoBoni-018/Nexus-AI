package com.nexusai.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public final class AiRepositories {

    private AiRepositories() {}

    public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
        Optional<Conversation> findByIdAndOrganizationIdAndUserId(UUID id, UUID organizationId, UUID userId);

        List<Conversation> findByOrganizationIdAndUserIdOrderByCreatedAtDesc(UUID organizationId, UUID userId,
                Pageable pageable);
    }

    public interface MessageRepository extends JpaRepository<ChatMessage, UUID> {
        List<ChatMessage> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

        List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
    }

    public interface ToolExecutionRepository extends JpaRepository<AiToolExecution, UUID> {
        List<AiToolExecution> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
    }
}
