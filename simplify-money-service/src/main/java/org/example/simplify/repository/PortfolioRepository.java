package org.example.simplify.repository;

import org.example.simplify.entity.PortfolioEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PortfolioRepository extends MongoRepository<PortfolioEntity, String> {
}

