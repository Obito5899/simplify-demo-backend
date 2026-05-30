package org.example.simplify.repository;

import org.example.simplify.entity.TransactionEntity;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TransactionRepository extends MongoRepository<TransactionEntity, String> {
    List<TransactionEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}

