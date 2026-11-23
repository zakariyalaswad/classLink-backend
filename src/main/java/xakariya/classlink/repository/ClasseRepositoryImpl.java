package xakariya.classlink.repository;

import org.springframework.stereotype.Repository;
import xakariya.classlink.domain.model.Classe;

import java.util.Optional;

@Repository
public class ClasseRepositoryImpl extends AbstractRepository<Classe> implements ClasseRepository {

    public ClasseRepositoryImpl(DocumentStore store) {
        super(store, "classes", Classe.class, Classe::getId, Classe::setId);
    }

    @Override
    public Optional<Classe> findByCodeAcces(String codeAcces) {
        if (codeAcces == null) return Optional.empty();
        return where("codeAcces", codeAcces.trim().toUpperCase()).stream().findFirst();
    }
}
