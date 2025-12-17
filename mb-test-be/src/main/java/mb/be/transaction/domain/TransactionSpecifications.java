package mb.be.transaction.domain;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

public final class TransactionSpecifications {

	private TransactionSpecifications() {}

	public static Specification<Transaction> customerIdEquals(String customerId) {
		return (root, query, cb) ->
				customerId == null || customerId.isBlank()
						? cb.conjunction()
						: cb.equal(root.get("customerId"), customerId);
	}

	public static Specification<Transaction> accountNumberIn(List<String> accountNumbers) {
		return (root, query, cb) -> {
			if (accountNumbers == null || accountNumbers.isEmpty()) return cb.conjunction();
			var accountJoin = root.join("account");
			return accountJoin.get("accountNumber").in(accountNumbers);
		};
	}

	public static Specification<Transaction> descriptionContains(String description) {
		return (root, query, cb) -> {
			if (description == null || description.isBlank()) return cb.conjunction();
			return cb.like(cb.lower(root.get("description")), "%" + description.toLowerCase() + "%");
		};
	}

	public static Specification<Transaction> trxDateBetween(LocalDate from, LocalDate to) {
		return (root, query, cb) -> {
			if (from == null && to == null) return cb.conjunction();
			if (from != null && to != null) return cb.between(root.get("trxDate"), from, to);
			if (from != null) return cb.greaterThanOrEqualTo(root.get("trxDate"), from);
			return cb.lessThanOrEqualTo(root.get("trxDate"), to);
		};
	}
}
