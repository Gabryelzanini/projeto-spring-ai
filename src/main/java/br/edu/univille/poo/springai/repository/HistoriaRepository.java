package br.edu.univille.poo.springai.repository;

import br.edu.univille.poo.springai.model.Historia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoriaRepository extends JpaRepository<Historia, Long> {
    List<Historia> findAllByOrderByDataAtualizacaoDesc();
}
