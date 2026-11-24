package com.odyssey.service;

import com.odyssey.dto.provider.AdminProviderActionRequest;
import com.odyssey.dto.provider.ProviderResponse;
import com.odyssey.entity.AuditEntryEntity;
import com.odyssey.entity.ProviderEntity;
import com.odyssey.entity.ServiceEntity;
import com.odyssey.entity.UserEntity;
import com.odyssey.exception.ResourceNotFoundException;
import com.odyssey.model.enums.AuditActor;
import com.odyssey.model.enums.ProviderStatus;
import com.odyssey.repository.jpa.AuditEntryJpaRepository;
import com.odyssey.repository.jpa.ProviderJpaRepository;
import com.odyssey.repository.jpa.ServiceJpaRepository;
import com.odyssey.util.IdGenerator;
import com.odyssey.util.NotificationMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProviderService {

    private final ProviderJpaRepository providerRepo;
    private final ServiceJpaRepository serviceRepo;
    private final AuditEntryJpaRepository auditRepo;
    private final AuthService authService;
    private final ProviderService providerService;

    @Transactional(readOnly = true)
    public List<ProviderResponse> getProviders(ProviderStatus status) {
        List<ProviderEntity> list;
        if (status != null) {
            list = providerRepo.findByStatusOrderByCreatedAtDesc(status);
        } else {
            list = providerRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return list.stream().map(providerService::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProviderResponse getProvider(String id) {
        ProviderEntity entity = providerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with ID: " + id));
        return providerService.toResponse(entity);
    }

    @Transactional
    public ProviderResponse approveProvider(String id) {
        ProviderEntity provider = providerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with ID: " + id));

        UserEntity admin = authService.getCurrentUserEntity();
        String adminIdentifier = admin != null ? admin.getEmail() : "ADMIN";

        provider.setStatus(ProviderStatus.APPROVED);
        provider.setReviewedBy(adminIdentifier);
        provider.setReviewedAt(LocalDateTime.now());
        provider.setStatusReason(null);
        providerRepo.save(provider);

        // Activate provider's services
        List<ServiceEntity> providerServices = serviceRepo.findByProviderId(id);
        for (ServiceEntity s : providerServices) {
            s.setActive(true);
            serviceRepo.save(s);
        }

        // Materialize future departures now that provider is approved
        try {
            providerService.materializeDeparturesForProvider(provider.getId());
        } catch (Exception e) {
            log.error("Error materialising departures for approved provider {}: {}", id, e.getMessage(), e);
        }

        recordAudit("PROVIDER_APPROVED", "Approved provider " + provider.getBusinessName() + " (ID: " + id + ")");
        log.info("Admin {} approved provider: id={}, name={}", NotificationMasker.maskEmail(adminIdentifier), id, provider.getBusinessName());

        return providerService.toResponse(provider);
    }

    @Transactional
    public ProviderResponse rejectProvider(String id, AdminProviderActionRequest request) {
        if (request == null || request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        ProviderEntity provider = providerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with ID: " + id));

        UserEntity admin = authService.getCurrentUserEntity();
        String adminIdentifier = admin != null ? admin.getEmail() : "ADMIN";

        provider.setStatus(ProviderStatus.REJECTED);
        provider.setReviewedBy(adminIdentifier);
        provider.setReviewedAt(LocalDateTime.now());
        provider.setStatusReason(request.getReason().trim());
        providerRepo.save(provider);

        // Deactivate provider's services
        List<ServiceEntity> providerServices = serviceRepo.findByProviderId(id);
        for (ServiceEntity s : providerServices) {
            s.setActive(false);
            serviceRepo.save(s);
        }

        recordAudit("PROVIDER_REJECTED", "Rejected provider " + provider.getBusinessName() + " (ID: " + id + "). Reason: " + request.getReason().trim());
        log.info("Admin {} rejected provider: id={}, name={}", NotificationMasker.maskEmail(adminIdentifier), id, provider.getBusinessName());

        return providerService.toResponse(provider);
    }

    @Transactional
    public ProviderResponse suspendProvider(String id, AdminProviderActionRequest request) {
        if (request == null || request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Suspension reason is required");
        }

        ProviderEntity provider = providerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with ID: " + id));

        UserEntity admin = authService.getCurrentUserEntity();
        String adminIdentifier = admin != null ? admin.getEmail() : "ADMIN";

        provider.setStatus(ProviderStatus.SUSPENDED);
        provider.setReviewedBy(adminIdentifier);
        provider.setReviewedAt(LocalDateTime.now());
        provider.setStatusReason(request.getReason().trim());
        providerRepo.save(provider);

        // Deactivate provider's services
        List<ServiceEntity> providerServices = serviceRepo.findByProviderId(id);
        for (ServiceEntity s : providerServices) {
            s.setActive(false);
            serviceRepo.save(s);
        }

        recordAudit("PROVIDER_SUSPENDED", "Suspended provider " + provider.getBusinessName() + " (ID: " + id + "). Reason: " + request.getReason().trim());
        log.info("Admin {} suspended provider: id={}, name={}", NotificationMasker.maskEmail(adminIdentifier), id, provider.getBusinessName());

        return providerService.toResponse(provider);
    }

    @Transactional
    public ProviderResponse reinstateProvider(String id) {
        ProviderEntity provider = providerRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with ID: " + id));

        UserEntity admin = authService.getCurrentUserEntity();
        String adminIdentifier = admin != null ? admin.getEmail() : "ADMIN";

        provider.setStatus(ProviderStatus.APPROVED);
        provider.setReviewedBy(adminIdentifier);
        provider.setReviewedAt(LocalDateTime.now());
        provider.setStatusReason(null);
        providerRepo.save(provider);

        // Activate provider's services
        List<ServiceEntity> providerServices = serviceRepo.findByProviderId(id);
        for (ServiceEntity s : providerServices) {
            s.setActive(true);
            serviceRepo.save(s);
        }

        // Re-materialize departures
        try {
            providerService.materializeDeparturesForProvider(provider.getId());
        } catch (Exception e) {
            log.error("Error re-materialising departures for reinstated provider {}: {}", id, e.getMessage(), e);
        }

        recordAudit("PROVIDER_REINSTATED", "Reinstated provider " + provider.getBusinessName() + " (ID: " + id + ")");
        log.info("Admin {} reinstated provider: id={}, name={}", NotificationMasker.maskEmail(adminIdentifier), id, provider.getBusinessName());

        return providerService.toResponse(provider);
    }

    private void recordAudit(String action, String details) {
        auditRepo.save(AuditEntryEntity.builder()
                .id(IdGenerator.generate("AUD"))
                .timestamp(LocalDateTime.now())
                .actor("ADMIN")
                .action(action)
                .details(details)
                .build());
    }
}
