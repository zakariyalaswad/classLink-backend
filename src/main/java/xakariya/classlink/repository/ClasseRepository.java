package xakariya.classlink.repository;

import xakariya.classlink.domain.model.Classe;

import java.util.Optional;

public interface ClasseRepository {
    Classe save(Classe classe);
    Optional<Classe> findById(String id);
    Optional<Classe> findByCodeAcces(String codeAcces);
    void deleteById(String id);
}
