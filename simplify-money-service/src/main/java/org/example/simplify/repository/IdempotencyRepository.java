package org.example.simplify.repository;

import org.example.simplify.entity.IdempotencyKeyEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IdempotencyRepository extends MongoRepository<IdempotencyKeyEntity, String> {
}

