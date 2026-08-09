package org.dave.middle.persistence.projection;

import org.dave.middle.domain.model.Country;
import org.dave.middle.domain.model.TransferStatus;

import java.math.BigDecimal;

public record TransferReportDto(String id, TransferStatus status, BigDecimal amount, Country from, Country to) {
}
