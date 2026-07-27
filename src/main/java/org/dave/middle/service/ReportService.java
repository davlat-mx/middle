package org.dave.middle.service;

import lombok.RequiredArgsConstructor;
import org.dave.middle.domain.model.Transfer;
import org.dave.middle.domain.model.TransferStatus;
import org.dave.middle.domain.vo.Corridor;
import org.dave.middle.repository.TransferRepository;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ReportService {

    private final TransferRepository repository;

    public Map<TransferStatus, Long> countByStatus() {
        return repository.findAll().stream()
                .collect(Collectors.groupingBy(
                        Transfer::getStatus,
                        () -> new EnumMap<>(TransferStatus.class),
                        Collectors.counting()));
    }

    public Map<Corridor, BigDecimal> turnoverByCorridor() {
        return repository.findAll().stream()
                .collect(Collectors.groupingBy(
                        Transfer::getCorridor,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                transfer -> transfer.getMoney().amount(),
                                BigDecimal::add)));
    }

    public Map<Boolean, List<Transfer>> successVsFailed() {
        return repository.findAll().stream()
                .collect(Collectors.partitioningBy(
                        transfer -> transfer.getStatus() != TransferStatus.FAILED));
    }
}
