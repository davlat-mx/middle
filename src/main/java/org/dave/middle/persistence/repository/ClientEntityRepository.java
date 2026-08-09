package org.dave.middle.persistence.repository;

import org.dave.middle.persistence.entity.ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientEntityRepository extends JpaRepository<ClientEntity, String> {
}
