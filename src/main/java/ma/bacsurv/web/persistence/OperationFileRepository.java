package ma.bacsurv.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperationFileRepository extends JpaRepository<OperationFile, Long> {

    List<OperationFile> findAllByOrderByUploadedAtDesc();
}
