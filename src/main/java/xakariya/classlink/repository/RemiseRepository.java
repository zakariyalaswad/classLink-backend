package xakariya.classlink.repository;

import xakariya.classlink.domain.model.Remise;

import java.util.List;
import java.util.Optional;

public interface RemiseRepository {
    Remise save(Remise remise);
    Optional<Remise> findById(String id);
    Optional<Remise> findByDevoirIdAndEtudiantId(String devoirId, String etudiantId);
    List<Remise> findByDevoirId(String devoirId);
    List<Remise> findByEtudiantId(String etudiantId);
    void deleteById(String id);
}
