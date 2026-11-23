package xakariya.classlink.repository;

import org.springframework.stereotype.Repository;
import xakariya.classlink.domain.model.Remise;

import java.util.List;
import java.util.Optional;

@Repository
public class RemiseRepositoryImpl extends AbstractRepository<Remise> implements RemiseRepository {

    public RemiseRepositoryImpl(DocumentStore store) {
        super(store, "remises", Remise.class, Remise::getId, Remise::setId);
    }

    @Override
    public Optional<Remise> findByDevoirIdAndEtudiantId(String devoirId, String etudiantId) {
        return where("devoirId", devoirId, "etudiantId", etudiantId).stream().findFirst();
    }

    @Override
    public List<Remise> findByDevoirId(String devoirId) {
        return where("devoirId", devoirId);
    }

    @Override
    public List<Remise> findByEtudiantId(String etudiantId) {
        return where("etudiantId", etudiantId);
    }
}
