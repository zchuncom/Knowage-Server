/*
 * Knowage, Open Source Business Intelligence suite
 * Copyright (C) 2016 Engineering Ingegneria Informatica S.p.A.
 * 
 * Knowage is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knowage is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.eng.spagobi.kpi.dao;

import it.eng.spago.error.EMFInternalError;
import it.eng.spagobi.commons.bo.Domain;
import it.eng.spagobi.commons.dao.AbstractHibernateDAO;
import it.eng.spagobi.commons.dao.ICriterion;
import it.eng.spagobi.commons.dao.IExecuteOnTransaction;
import it.eng.spagobi.commons.dao.SpagoBIDAOObjectNotExistingException;
import it.eng.spagobi.commons.dao.SpagoBIDOAException;
import it.eng.spagobi.commons.metadata.SbiDomains;
import it.eng.spagobi.commons.utilities.messages.IMessageBuilder;
import it.eng.spagobi.commons.utilities.messages.MessageBuilderFactory;
import it.eng.spagobi.kpi.bo.Alias;
import it.eng.spagobi.kpi.bo.Cardinality;
import it.eng.spagobi.kpi.bo.Kpi;
import it.eng.spagobi.kpi.bo.Placeholder;
import it.eng.spagobi.kpi.bo.Rule;
import it.eng.spagobi.kpi.bo.RuleOutput;
import it.eng.spagobi.kpi.bo.Threshold;
import it.eng.spagobi.kpi.bo.ThresholdValue;
import it.eng.spagobi.kpi.metadata.SbiKpiAlias;
import it.eng.spagobi.kpi.metadata.SbiKpiKpi;
import it.eng.spagobi.kpi.metadata.SbiKpiPlaceholder;
import it.eng.spagobi.kpi.metadata.SbiKpiRule;
import it.eng.spagobi.kpi.metadata.SbiKpiRuleOutput;
import it.eng.spagobi.kpi.metadata.SbiKpiThreshold;
import it.eng.spagobi.kpi.metadata.SbiKpiThresholdValue;
import it.eng.spagobi.utilities.exceptions.SpagoBIException;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.hibernate.Criteria;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.transform.Transformers;

public class KpiDAOImpl extends AbstractHibernateDAO implements IKpiDAO {

	private static final String NEW_KPI_ALIAS_NOT_AVAILABLE_AS_MEASURE = "newKpi.aliasNotAvailableAsMeasure";
	private static final String NEW_KPI_ALIAS_NOT_AVAILABLE = "newKpi.aliasNotAvailable";
	private static final String NEW_KPI_KPI_NAME_NOT_AVAILABLE = "newKpi.kpiNameNotAvailable";
	private static final String NEW_KPI_KPI_NOT_FOUND = "newKpi.kpiNotFound";
	private static final String KPI_MEASURE_CATEGORY = "KPI_MEASURE_CATEGORY";
	private static final String KPI_KPI_CATEGORY = "KPI_KPI_CATEGORY";
	private static final String MEASURE = "MEASURE";

	private static IMessageBuilder message = MessageBuilderFactory.getMessageBuilder();

	@Override
	public List<String> listPlaceholderByMeasures(final List<String> measures) {
		List<SbiKpiPlaceholder> lst = list(new ICriterion<SbiKpiPlaceholder>() {
			@Override
			public Criteria evaluate(Session session) {
				DetachedCriteria detachedCriteria = DetachedCriteria.forClass(SbiKpiRuleOutput.class).createAlias("sbiKpiRule", "sbiKpiRule")
						.createAlias("sbiKpiAlias", "sbiKpiAlias").setProjection(Property.forName("sbiKpiRule.id"))
						.add(Restrictions.in("sbiKpiAlias.name", measures));

				Criteria c = session.createCriteria(SbiKpiRule.class).createAlias("sbiKpiPlaceholders", "sbiKpiPlaceholders")
						.add(Property.forName("id").in(detachedCriteria))
						.setProjection(Projections.distinct(Projections.property("sbiKpiPlaceholders.name").as("name")))
						.setResultTransformer(Transformers.aliasToBean(SbiKpiPlaceholder.class));
				return c;
			}
		});
		List<String> placeholdername = new ArrayList<>();
		for (SbiKpiPlaceholder sbiKpiPlaceholder : lst) {
			placeholdername.add(sbiKpiPlaceholder.getName());
		}
		return placeholdername;
	}

	@Override
	public List<Cardinality> buildCardinality(final List<String> measures) {
		return executeOnTransaction(new IExecuteOnTransaction<List<Cardinality>>() {
			@Override
			public List<Cardinality> execute(Session session) throws Exception {
				DetachedCriteria detachedCriteria = DetachedCriteria.forClass(SbiKpiRuleOutput.class).createAlias("sbiKpiRule", "sbiKpiRule")
						.createAlias("sbiKpiAlias", "sbiKpiAlias").setProjection(Property.forName("sbiKpiRule.id"))
						.add(Restrictions.in("sbiKpiAlias.name", measures));

				List<SbiKpiRuleOutput> allRuleOutputs = session.createCriteria(SbiKpiRuleOutput.class).createAlias("sbiKpiRule", "sbiKpiRule")
						.add(Property.forName("sbiKpiRule.id").in(detachedCriteria)).list();

				Map<Integer, Cardinality> cardinalityMap = new HashMap<>();

				for (SbiKpiRuleOutput sbiRuleOutput : allRuleOutputs) {
					Cardinality cardinality = cardinalityMap.get(sbiRuleOutput.getSbiKpiRule().getId());
					if (MEASURE.equals(sbiRuleOutput.getType().getValueCd())) {
						if (cardinality == null) {
							cardinality = new Cardinality();
							cardinality.setRuleId(sbiRuleOutput.getSbiKpiRule().getId());
							cardinality.setRuleName(sbiRuleOutput.getSbiKpiRule().getName());
						}
						cardinality.setMeasureName(sbiRuleOutput.getSbiKpiAlias().getName());
					} else {
						if (cardinality == null) {
							cardinality = new Cardinality();
							cardinality.setRuleId(sbiRuleOutput.getSbiKpiRule().getId());
							cardinality.setRuleName(sbiRuleOutput.getSbiKpiRule().getName());
						}
						cardinality.getAttributes().put(sbiRuleOutput.getSbiKpiAlias().getName(), Boolean.FALSE);
					}
				}

				List<Cardinality> cardinalityOrdered = new ArrayList<>();
				Collection<Cardinality> cardinality = cardinalityMap.values();
				// output order has to be the same one as input measures list
				for (String measure : measures) {
					for (Cardinality c : cardinality) {
						if (measure.equals(c.getMeasureName())) {
							cardinality.add(c);
						}
					}
				}
				return cardinalityOrdered;
			}
		});
	}

	@Override
	public Integer insertRule(final Rule rule) {
		if (rule.getName() != null && !rule.getName().isEmpty()) {
			boolean isNameAvailable = list(new ICriterion<SbiKpiRule>() {
				@Override
				public Criteria evaluate(Session session) {
					return session.createCriteria(SbiKpiRule.class).add(Restrictions.eq("name", rule.getName())).setMaxResults(1);
				}
			}).isEmpty();
			if (!isNameAvailable) {
				throw new SpagoBIDOAException("Rule name not available [" + rule.getName() + "]");
			}
		}

		return executeOnTransaction(new IExecuteOnTransaction<Integer>() {
			@Override
			public Integer execute(Session session) throws SpagoBIException {
				ruleValidationForInsert(session, rule);

				SbiKpiRule sbiRule = new SbiKpiRule();
				sbiRule.setName(rule.getName());
				sbiRule.setDefinition(rule.getDefinition());
				sbiRule.setDataSourceId(rule.getDataSourceId());
				// handling RuleOutputs
				for (RuleOutput ruleOutput : rule.getRuleOutputs()) {
					SbiKpiRuleOutput sbiRuleOutput = new SbiKpiRuleOutput();
					sbiRuleOutput.setSbiKpiRule(sbiRule);
					if (ruleOutput.getType() != null) {
						sbiRuleOutput.setTypeId(ruleOutput.getType().getValueId());
					}
					// handling Alias
					SbiKpiAlias sbiAlias = manageAlias(session, ruleOutput.getAliasId(), ruleOutput.getAlias());
					sbiRuleOutput.setSbiKpiAlias(sbiAlias);
					// handling Category
					SbiDomains category = insertOrUpdateCategory(session, ruleOutput.getCategory(), KPI_MEASURE_CATEGORY);
					sbiRuleOutput.setCategory(category);

					updateSbiCommonInfo4Insert(sbiRuleOutput);
					sbiRule.getSbiKpiRuleOutputs().add(sbiRuleOutput);
				}
				// handling Placeholders
				for (Placeholder placeholder : rule.getPlaceholders()) {
					SbiKpiPlaceholder sbiKpiPlaceholder = managePlaceholder(session, placeholder, rule);
					sbiRule.getSbiKpiPlaceholders().add(sbiKpiPlaceholder);
				}
				updateSbiCommonInfo4Insert(sbiRule);

				return (Integer) session.save(sbiRule);
			}
		});
	}

	@Override
	public void updateRule(final Rule rule) {
		if (rule.getName() != null && !rule.getName().isEmpty()) {
			boolean isNameAvailable = list(new ICriterion<SbiKpiRule>() {
				@Override
				public Criteria evaluate(Session session) {
					return session.createCriteria(SbiKpiRule.class).add(Restrictions.eq("name", rule.getName())).add(Restrictions.ne("id", rule.getId()))
							.setMaxResults(1);
				}
			}).isEmpty();
			if (!isNameAvailable) {
				throw new SpagoBIDOAException("Rule name not available [" + rule.getName() + "]");
			}
		}
		executeOnTransaction(new IExecuteOnTransaction<Boolean>() {
			@Override
			public Boolean execute(Session session) throws EMFInternalError, SpagoBIException {
				ruleValidationForUpdate(session, rule);
				SbiKpiRule sbiRule = (SbiKpiRule) session.load(SbiKpiRule.class, rule.getId());
				sbiRule.setName(rule.getName());
				sbiRule.setDefinition(rule.getDefinition());
				sbiRule.setDataSourceId(rule.getDataSourceId());

				// clearing removed references
				Iterator<SbiKpiRuleOutput> iterator = sbiRule.getSbiKpiRuleOutputs().iterator();
				while (iterator.hasNext()) {
					SbiKpiRuleOutput sbiKpiRuleOutput = iterator.next();
					if (rule.getRuleOutputs().size() > 0 && rule.getRuleOutputs().indexOf(new RuleOutput(sbiKpiRuleOutput.getId())) == -1) {
						iterator.remove();
					}
				}
				// handling RuleOutputs
				for (RuleOutput ruleOutput : rule.getRuleOutputs()) {
					SbiKpiRuleOutput sbiRuleOutput = manageRuleOutput(session, sbiRule, ruleOutput);

					if (ruleOutput.getType() != null) {
						sbiRuleOutput.setTypeId(ruleOutput.getType().getValueId());
					}
					// handling Alias
					SbiKpiAlias sbiAlias = manageAlias(session, ruleOutput.getAliasId(), ruleOutput.getAlias());

					sbiRuleOutput.setSbiKpiAlias(sbiAlias);
					// handling Category
					SbiDomains category = insertOrUpdateCategory(session, ruleOutput.getCategory(), KPI_MEASURE_CATEGORY);
					sbiRuleOutput.setCategory(category);

					sbiRule.getSbiKpiRuleOutputs().add(sbiRuleOutput);
				}

				// handling Placeholders
				for (Placeholder placeholder : rule.getPlaceholders()) {
					SbiKpiPlaceholder sbiKpiPlaceholder = managePlaceholder(session, placeholder, rule);
					sbiRule.getSbiKpiPlaceholders().add(sbiKpiPlaceholder);
				}
				updateSbiCommonInfo4Update(sbiRule);

				session.save(sbiRule);
				return Boolean.TRUE;
			}

		});
	}

	private SbiKpiRuleOutput manageRuleOutput(Session session, SbiKpiRule sbiRule, RuleOutput ruleOutput) {
		SbiKpiRuleOutput sbiRuleOutput = null;
		if (ruleOutput.getId() == null) {
			sbiRuleOutput = new SbiKpiRuleOutput();
			sbiRuleOutput.setSbiKpiRule(sbiRule);
			updateSbiCommonInfo4Insert(sbiRuleOutput);
		} else {
			sbiRuleOutput = (SbiKpiRuleOutput) session.load(SbiKpiRuleOutput.class, ruleOutput.getId());
			updateSbiCommonInfo4Update(sbiRuleOutput);
		}
		return sbiRuleOutput;
	}

	@Override
	public void removeRule(final Integer id) {
		executeOnTransaction(new IExecuteOnTransaction<Boolean>() {
			@Override
			public Boolean execute(Session session) throws Exception {
				SbiKpiRule rule = (SbiKpiRule) session.load(SbiKpiRule.class, id);

				// Deleting placeholders
				for (SbiKpiPlaceholder sbiKpiPlaceholder : rule.getSbiKpiPlaceholders()) {
					session.delete(sbiKpiPlaceholder);
				}

				// Deleting Rule
				session.delete(rule);
				session.flush();

				// Deleting unused categories
				DetachedCriteria usedCategories = DetachedCriteria.forClass(SbiKpiRuleOutput.class)// .add(Restrictions.ne("sbiKpiRule", rule))
						.createAlias("category", "category").add(Restrictions.isNotNull("category")).setProjection(Property.forName("category.valueId"));
				List<SbiDomains> categoriesToDelete = session.createCriteria(SbiDomains.class).add(Restrictions.eq("domainCd", KPI_MEASURE_CATEGORY))
						.add(Property.forName("valueId").notIn(usedCategories)).list();
				for (SbiDomains cat : categoriesToDelete) {
					session.delete(cat);
				}

				// Deleting unused aliases
				DetachedCriteria usedAliases = DetachedCriteria.forClass(SbiKpiRuleOutput.class)// .add(Restrictions.ne("sbiKpiRule", rule))
						.createAlias("sbiKpiAlias", "sbiKpiAlias").setProjection(Property.forName("sbiKpiAlias.id"));
				List<SbiKpiAlias> aliasesToDelete = session.createCriteria(SbiKpiAlias.class).add(Property.forName("id").notIn(usedAliases)).list();
				for (SbiKpiAlias alias : aliasesToDelete) {
					session.delete(alias);
				}

				return Boolean.TRUE;
			}
		});
	}

	@Override
	public Rule loadRule(final Integer id) {
		SbiKpiRule rule = executeOnTransaction(new IExecuteOnTransaction<SbiKpiRule>() {
			@Override
			public SbiKpiRule execute(Session session) throws Exception {
				SbiKpiRule rule = (SbiKpiRule) session.load(SbiKpiRule.class, id);
				Hibernate.initialize(rule.getSbiKpiPlaceholders());
				Hibernate.initialize(rule.getSbiKpiRuleOutputs());
				return rule;
			}
		});
		// SbiKpiRule rule = load(SbiKpiRule.class, id);
		// List<SbiKpiRuleOutput> sbiKpiRuleOutputs = list(new ICriterion<SbiKpiRuleOutput>() {
		// @Override
		// public Criteria evaluate(Session session) {
		// return session.createCriteria(SbiKpiRuleOutput.class).createAlias("sbiKpiRule", "sbiKpiRule").add(Restrictions.eq("sbiKpiRule.id", id));
		// }
		// });
		// list(new ICriterion<SbiKpiPlaceholder>() {
		// @Override
		// public Criteria evaluate(Session session) {
		// Criteria c = session.createCriteria(SbiKpiRule.class).createAlias("sbiKpiPlaceholders", "sbiKpiPlaceholders")
		// .add(Property.forName("id").in(detachedCriteria))
		// .setProjection(Projections.distinct(Projections.property("sbiKpiPlaceholders.name").as("name")))
		// .setResultTransformer(Transformers.aliasToBean(SbiKpiPlaceholder.class));
		// return c;
		// }
		// });
		return from(rule);
	}

	@Override
	public List<Kpi> listKpi() {
		List<SbiKpiKpi> lst = list(SbiKpiKpi.class);
		List<Kpi> kpis = new ArrayList<>();
		for (SbiKpiKpi sbi : lst) {
			Kpi kpi = from(sbi, null, false);
			kpis.add(kpi);
		}
		return kpis;
	}

	@Override
	public void insertKpi(final Kpi kpi) {
		if (kpi.getName() != null && !kpi.getName().isEmpty()) {
			boolean isNameAvailable = list(new ICriterion<SbiKpiKpi>() {
				@Override
				public Criteria evaluate(Session session) {
					return session.createCriteria(SbiKpiKpi.class).add(Restrictions.eq("name", kpi.getName())).setMaxResults(1);
				}
			}).isEmpty();
			if (!isNameAvailable) {
				throw new SpagoBIDOAException(MessageFormat.format(message.getMessage(NEW_KPI_KPI_NAME_NOT_AVAILABLE), kpi.getName()));
			}
		}
		executeOnTransaction(new IExecuteOnTransaction<Boolean>() {
			@Override
			public Boolean execute(Session session) {
				SbiKpiKpi sbiKpi = from(session, null, kpi);
				updateSbiCommonInfo4Insert(sbiKpi);
				session.save(sbiKpi);
				return Boolean.TRUE;
			}

		});
	}

	@Override
	public void updateKpi(final Kpi kpi) {
		if (kpi.getName() != null && !kpi.getName().isEmpty()) {
			boolean isNameAvailable = list(new ICriterion<SbiKpiKpi>() {
				@Override
				public Criteria evaluate(Session session) {
					return session.createCriteria(SbiKpiKpi.class).add(Restrictions.eq("name", kpi.getName())).add(Restrictions.ne("id", kpi.getId()))
							.setMaxResults(1);
				}
			}).isEmpty();
			if (!isNameAvailable) {
				throw new SpagoBIDOAException(MessageFormat.format(message.getMessage(NEW_KPI_KPI_NAME_NOT_AVAILABLE), kpi.getName()));
			}
		}
		executeOnTransaction(new IExecuteOnTransaction<Boolean>() {
			@Override
			public Boolean execute(Session session) {
				SbiKpiKpi sbiKpi = (SbiKpiKpi) session.get(SbiKpiKpi.class, kpi.getId());
				if (sbiKpi == null) {
					throw new SpagoBIDAOObjectNotExistingException(MessageFormat.format(message.getMessage(NEW_KPI_KPI_NOT_FOUND), kpi.getId()));
				}
				sbiKpi = from(session, sbiKpi, kpi);
				updateSbiCommonInfo4Update(sbiKpi);
				session.save(sbiKpi);
				return Boolean.TRUE;
			}
		});
	}

	private SbiKpiKpi from(Session session, SbiKpiKpi sbiKpi, Kpi kpi) {
		if (sbiKpi == null) {
			sbiKpi = new SbiKpiKpi();
			sbiKpi.setId(kpi.getId());
		}
		sbiKpi.setCardinality(kpi.getCardinality());
		sbiKpi.setDefinition(kpi.getDefinition());
		sbiKpi.setName(kpi.getName());
		sbiKpi.setPlaceholder(kpi.getPlaceholder());
		if (kpi.getThreshold() == null) {
			throw new SpagoBIDOAException("Threshold is mandatory");
		}
		if (kpi.getThreshold().getId() == null) {
			SbiKpiThreshold sbiKpiThreshold = from(session, null, kpi.getThreshold());
			updateSbiCommonInfo4Insert(sbiKpiThreshold);
			sbiKpi.setThresholdId((Integer) session.save(sbiKpiThreshold));
		} else {
			sbiKpi.setThresholdId(kpi.getThreshold().getId());
		}
		// handling Category
		SbiDomains category = insertOrUpdateCategory(session, kpi.getCategory(), KPI_KPI_CATEGORY);
		sbiKpi.setCategory(category);
		return sbiKpi;
	}

	private SbiKpiAlias manageAlias(Session session, Integer aliasId, String aliasName) {
		SbiKpiAlias alias = null;
		if (aliasId != null) {
			alias = (SbiKpiAlias) session.load(SbiKpiAlias.class, aliasId);
		} else if (aliasName != null && !aliasName.isEmpty()) {
			alias = (SbiKpiAlias) session.createCriteria(SbiKpiAlias.class).add(Restrictions.eq("name", aliasName)).uniqueResult();
			if (alias == null) {
				alias = new SbiKpiAlias();
				alias.setName(aliasName);
				updateSbiCommonInfo4Insert(alias);
			}
		}
		return alias;
	}

	private SbiKpiPlaceholder managePlaceholder(Session session, Placeholder placeholder, Rule rule) {
		SbiKpiPlaceholder sbiKpiPlaceholder = null;
		if (placeholder.getId() != null) {
			sbiKpiPlaceholder = (SbiKpiPlaceholder) session.load(SbiKpiPlaceholder.class, placeholder.getId());
		} else if (placeholder.getName() != null && !placeholder.getName().isEmpty()) {
			sbiKpiPlaceholder = (SbiKpiPlaceholder) session.createCriteria(SbiKpiPlaceholder.class).add(Restrictions.eq("name", placeholder.getName()))
					.uniqueResult();
			if (sbiKpiPlaceholder == null) {
				sbiKpiPlaceholder = new SbiKpiPlaceholder();
				sbiKpiPlaceholder.setName(placeholder.getName());
				updateSbiCommonInfo4Insert(sbiKpiPlaceholder);
			}
		}
		return sbiKpiPlaceholder;
	}

	private SbiDomains insertOrUpdateCategory(Session session, Domain category, String categoryName) {
		SbiDomains cat = null;
		if (category != null) {
			if (category.getValueId() != null) {
				cat = (SbiDomains) session.load(SbiDomains.class, category.getValueId());
			} else if (category.getValueCd() != null) {
				cat = (SbiDomains) session.createCriteria(SbiDomains.class).add(Restrictions.eq("domainCd", categoryName))
						.add(Restrictions.eq("valueCd", category.getValueCd())).uniqueResult();
				if (cat == null) {
					cat = new SbiDomains();
					cat.setDomainCd(categoryName);
					cat.setDomainNm(categoryName);
					cat.setValueCd(category.getValueCd());
					cat.setValueNm(category.getValueCd());
					cat.setValueDs(category.getValueCd());
					updateSbiCommonInfo4Insert(cat);
					session.save(cat);
				}
			}
		}
		return cat;
	}

	/**
	 * Delete category after checking if no other Kpi object is using it
	 * 
	 * @param session
	 * @param category
	 * @param kpi
	 */
	private void removeKpiCategory(Session session, SbiDomains category, Integer kpiId) {
		// check if no other objects are using this category
		if (session.createCriteria(SbiKpiKpi.class)
		// .createAlias("category", "category").add(Restrictions.eq("category.valueId", category.getValueId()))
				.add(Restrictions.eq("category", category)).add(Restrictions.ne("id", kpiId)).setMaxResults(1).uniqueResult() == null) {
			// category is not used so can be deleted
			session.delete(category);
		}
	}

	/**
	 * Delete category after checking if no other Measure (ie RuleOutput of type "measure") is using it
	 * 
	 * @param session
	 * @param category
	 * @param ruleOutputId
	 */
	private void removeMeasureCategory(Session session, SbiDomains category, Integer ruleOutputId) {
		// check if no other objects are using this category
		if (session.createCriteria(SbiKpiRuleOutput.class).createAlias("category", "category").add(Restrictions.eq("category.valueId", category.getValueId()))
				.add(Restrictions.ne("id", ruleOutputId)).setMaxResults(1).uniqueResult() == null) {
			// category is not used so can be deleted
			session.delete(category);
		}
	}

	@Override
	public void removeKpi(final Integer id) {
		executeOnTransaction(new IExecuteOnTransaction<Boolean>() {
			@Override
			public Boolean execute(Session session) throws Exception {
				SbiKpiKpi kpi = (SbiKpiKpi) session.load(SbiKpiKpi.class, id);
				session.delete(kpi);

				removeKpiCategory(session, kpi.getCategory(), kpi.getId());

				return Boolean.TRUE;
			}
		});
	}

	@Override
	public Kpi loadKpi(final Integer id) {
		return executeOnTransaction(new IExecuteOnTransaction<Kpi>() {
			@Override
			public Kpi execute(Session session) {
				SbiKpiKpi sbiKpi = (SbiKpiKpi) session.load(SbiKpiKpi.class, id);
				SbiKpiThreshold sbiKpiThreshold = (SbiKpiThreshold) session.load(SbiKpiThreshold.class, sbiKpi.getThresholdId());
				return from(sbiKpi, sbiKpiThreshold, true);
			}
		});
	}

	@Override
	public List<Alias> listAlias() {
		List<Alias> ret = new ArrayList<>();
		List<SbiKpiAlias> sbiAlias = list(SbiKpiAlias.class);
		for (SbiKpiAlias sbiKpiAlias : sbiAlias) {
			ret.add(new Alias(sbiKpiAlias.getId(), sbiKpiAlias.getName()));
		}
		return ret;
	}

	@Override
	public List<Alias> listAliasNotInMeasure(final Integer ruleId) {
		List<SbiKpiAlias> sbiAlias = list(new ICriterion<SbiKpiAlias>() {
			@Override
			public Criteria evaluate(Session session) {
				DetachedCriteria detachedcriteria = DetachedCriteria.forClass(SbiKpiRuleOutput.class).createAlias("sbiKpiAlias", "sbiKpiAlias")
						.createAlias("sbiKpiRule", "sbiKpiRule").setProjection(Property.forName("sbiKpiAlias.name")).createAlias("type", "type")
						.add(Restrictions.eq("type.valueCd", MEASURE));
				if (ruleId != null) {
					detachedcriteria.add(Restrictions.ne("sbiKpiRule.id", ruleId));
				}
				Criteria c = session.createCriteria(SbiKpiAlias.class).add(Property.forName("name").notIn(detachedcriteria));
				return c;
			}
		});
		List<Alias> ret = new ArrayList<>();
		for (SbiKpiAlias sbiKpiAlias : sbiAlias) {
			ret.add(new Alias(sbiKpiAlias.getId(), sbiKpiAlias.getName()));
		}
		return ret;
	}

	@Override
	public Alias loadAlias(final String name) {
		List<SbiKpiAlias> aliases = list(new ICriterion<SbiKpiAlias>() {
			@Override
			public Criteria evaluate(Session session) {
				return session.createCriteria(SbiKpiAlias.class).add(Restrictions.eq("name", name));
			}
		});
		if (aliases != null && !aliases.isEmpty()) {
			SbiKpiAlias alias = aliases.get(0);
			return new Alias(alias.getId(), alias.getName());
		}
		return null;
	}

	@Override
	public List<Placeholder> listPlaceholder() {
		List<Placeholder> ret = new ArrayList<>();
		List<SbiKpiPlaceholder> sbiAlias = list(SbiKpiPlaceholder.class);
		for (SbiKpiPlaceholder sbi : sbiAlias) {
			ret.add(new Placeholder(sbi.getId(), sbi.getName()));
		}
		return ret;
	}

	@Override
	public List<RuleOutput> listRuleOutputByType(final String type) {
		List<SbiKpiRuleOutput> sbiRuleOutputs = list(new ICriterion<SbiKpiRuleOutput>() {
			@Override
			public Criteria evaluate(Session session) {
				// Ordering by rule name and measure name
				return session.createCriteria(SbiKpiRuleOutput.class).createAlias("type", "type").createAlias("sbiKpiAlias", "sbiKpiAlias")
						.createAlias("sbiKpiRule", "sbiKpiRule").add(Restrictions.eq("type.valueCd", type)).addOrder(Order.asc("sbiKpiRule.name"))
						.addOrder(Order.asc("sbiKpiAlias.name"));
			}
		});

		List<RuleOutput> ruleOutputs = new ArrayList<>();
		for (SbiKpiRuleOutput sbiKpiRuleOutput : sbiRuleOutputs) {
			ruleOutputs.add(from(sbiKpiRuleOutput));

		}
		return ruleOutputs;
	}

	private ThresholdValue from(SbiKpiThresholdValue sbiValue) {
		ThresholdValue tv = new ThresholdValue();
		tv.setId(sbiValue.getId());
		tv.setColor(sbiValue.getColor());
		tv.setLabel(sbiValue.getLabel());
		tv.setPosition(sbiValue.getPosition());
		tv.setSeverity(MessageBuilderFactory.getMessageBuilder().getMessage(sbiValue.getSeverity().getValueNm()));// sbiValue.getSeverity().getValueNm());
		tv.setSeverityId(sbiValue.getSeverity().getValueId());
		return tv;
	}

	private SbiKpiThreshold from(Session session, SbiKpiThreshold sbiKpiThreshold, Threshold t) {
		if (sbiKpiThreshold == null) {
			sbiKpiThreshold = new SbiKpiThreshold();
			sbiKpiThreshold.setId(t.getId());
		}
		sbiKpiThreshold.setName(t.getName());
		sbiKpiThreshold.setDescription(t.getDescription());
		sbiKpiThreshold.setType(new SbiDomains(t.getTypeId()));
		Map<Integer, SbiKpiThresholdValue> oldValueMap = new HashMap<>();
		for (Object obj : sbiKpiThreshold.getSbiKpiThresholdValues()) {
			SbiKpiThresholdValue oldValue = (SbiKpiThresholdValue) obj;
			oldValueMap.put(oldValue.getId(), oldValue);
		}
		for (ThresholdValue tv : t.getThresholdValues()) {
			SbiKpiThresholdValue sbiThresholdValue = null;
			if (tv.getId() == null) {
				sbiThresholdValue = from(sbiThresholdValue, tv);
				updateSbiCommonInfo4Insert(sbiThresholdValue);
				sbiThresholdValue.setSbiKpiThreshold(sbiKpiThreshold);
				sbiKpiThreshold.getSbiKpiThresholdValues().add(sbiThresholdValue);
			} else {
				sbiThresholdValue = oldValueMap.get(tv.getId());
				oldValueMap.remove(tv.getId());
				sbiThresholdValue = from(sbiThresholdValue, tv);
				updateSbiCommonInfo4Update(sbiThresholdValue);
			}
		}
		Iterator<SbiKpiThresholdValue> iterator = sbiKpiThreshold.getSbiKpiThresholdValues().iterator();
		while (iterator.hasNext()) {
			SbiKpiThresholdValue oldValue = iterator.next();
			if (oldValueMap.get(oldValue.getId()) != null) {
				iterator.remove();
			}
		}
		return sbiKpiThreshold;
	}

	private SbiKpiThresholdValue from(SbiKpiThresholdValue sbiValue, ThresholdValue tv) {
		if (sbiValue == null) {
			sbiValue = new SbiKpiThresholdValue();
		}
		sbiValue.setId(tv.getId());
		sbiValue.setColor(tv.getColor());
		char isIncludeMax = tv.isIncludeMax() ? 'T' : 'F';
		sbiValue.setIncludeMax(isIncludeMax);
		char isIncludeMin = tv.isIncludeMin() ? 'T' : 'F';
		sbiValue.setIncludeMin(isIncludeMin);
		sbiValue.setMaxValue(tv.getMaxValue());
		sbiValue.setMinValue(tv.getMinValue());
		sbiValue.setLabel(tv.getLabel());
		sbiValue.setPosition(tv.getPosition());
		sbiValue.setSeverity(new SbiDomains(tv.getSeverityId()));
		return sbiValue;
	}

	@Override
	public List<Threshold> listThreshold() {
		List<SbiKpiThreshold> sbiLst = list(SbiKpiThreshold.class);
		List<Threshold> thresholds = new ArrayList<>();
		for (SbiKpiThreshold sbiThreshold : sbiLst) {
			thresholds.add(from(sbiThreshold, false));
		}
		return thresholds;
	}

	@Override
	public RuleOutput loadMeasureByName(final String name) {
		List<SbiKpiRuleOutput> ruleOutputs = list(new ICriterion<SbiKpiRuleOutput>() {
			@Override
			public Criteria evaluate(Session session) {
				return session.createCriteria(SbiKpiRuleOutput.class).createAlias("type", "type").createAlias("sbiKpiAlias", "sbiKpiAlias")
						.add(Restrictions.eq("type.valueCd", MEASURE)).add(Restrictions.eq("sbiKpiAlias.name", name));
			}
		});
		if (ruleOutputs != null && !ruleOutputs.isEmpty()) {
			return from(ruleOutputs.get(0));
		}
		return null;
	}

	private RuleOutput from(SbiKpiRuleOutput sbiKpiRuleOutput) {
		RuleOutput ruleOutput = new RuleOutput();
		ruleOutput.setId(sbiKpiRuleOutput.getId());
		ruleOutput.setAlias(sbiKpiRuleOutput.getSbiKpiAlias().getName());
		ruleOutput.setAliasId(sbiKpiRuleOutput.getSbiKpiAlias().getId());
		if (sbiKpiRuleOutput.getCategory() != null) {
			Domain category = new Domain();
			category.setValueId(sbiKpiRuleOutput.getCategory().getValueId());
			category.setValueCd(sbiKpiRuleOutput.getCategory().getValueCd());
			ruleOutput.setCategory(category);
		}
		if (sbiKpiRuleOutput.getType() != null) {
		}
		ruleOutput.setType(from(sbiKpiRuleOutput.getType()));
		// Fields from Rule: Rule Id, Rule Name, Author, Date Creation
		ruleOutput.setRuleId(sbiKpiRuleOutput.getSbiKpiRule().getId());
		ruleOutput.setRule(sbiKpiRuleOutput.getSbiKpiRule().getName());
		ruleOutput.setAuthor(sbiKpiRuleOutput.getSbiKpiRule().getCommonInfo().getUserIn());
		ruleOutput.setDateCreation(sbiKpiRuleOutput.getSbiKpiRule().getCommonInfo().getTimeIn());
		return ruleOutput;
	}

	private Domain from(SbiDomains sbiType) {
		Domain type = new Domain();
		type.setDomainCode(sbiType.getDomainCd());
		type.setDomainName(sbiType.getDomainNm());
		type.setValueCd(sbiType.getValueCd());
		type.setValueDescription(sbiType.getValueDs());
		type.setValueName(sbiType.getValueNm());
		type.setValueId(sbiType.getValueId());
		return type;
	}

	private Kpi from(SbiKpiKpi sbi, SbiKpiThreshold sbiKpiThreshold, boolean full) {
		Kpi kpi = new Kpi();
		kpi.setId(sbi.getId());
		kpi.setCardinality(sbi.getCardinality());
		if (sbi.getCategory() != null) {
			kpi.setCategory(from(sbi.getCategory()));
		}
		kpi.setDefinition(sbi.getDefinition());
		kpi.setName(sbi.getName());
		kpi.setPlaceholder(sbi.getPlaceholder());
		if (sbiKpiThreshold != null && full) {
			kpi.setThreshold(from(sbiKpiThreshold, full));
		}
		kpi.setAuthor(sbi.getCommonInfo().getUserIn());
		kpi.setDateCreation(sbi.getCommonInfo().getTimeIn());
		return kpi;
	}

	private Threshold from(SbiKpiThreshold sbiKpiThreshold, boolean full) {
		Threshold threshold = new Threshold();
		threshold.setId(sbiKpiThreshold.getId());
		threshold.setName(sbiKpiThreshold.getName());
		threshold.setDescription(sbiKpiThreshold.getDescription());
		threshold.setType(MessageBuilderFactory.getMessageBuilder().getMessage(sbiKpiThreshold.getType().getValueNm()));// sbiKpiThreshold.getType().getValueNm());
		threshold.setTypeId(sbiKpiThreshold.getType().getValueId());
		if (full) {
			for (Object obj : sbiKpiThreshold.getSbiKpiThresholdValues()) {
				SbiKpiThresholdValue sbiValue = (SbiKpiThresholdValue) obj;
				ThresholdValue tv = new ThresholdValue();
				tv.setColor(sbiValue.getColor());
				tv.setId(sbiValue.getId());
				if (sbiKpiThreshold.getType().getValueCd().equals("RANGE") || sbiKpiThreshold.getType().getValueCd().equals("MINIMUM")) {
					tv.setIncludeMin(sbiValue.getIncludeMin() != null && sbiValue.getIncludeMin().charValue() == 'T');
					tv.setMinValue(sbiValue.getMinValue());
				} else if (sbiKpiThreshold.getType().getValueCd().equals("RANGE") || sbiKpiThreshold.getType().getValueCd().equals("MAXIMUM")) {
					tv.setIncludeMax(sbiValue.getIncludeMax() != null && sbiValue.getIncludeMax().charValue() == 'T');
					tv.setMaxValue(sbiValue.getMaxValue());
				}
				tv.setLabel(sbiValue.getLabel());
				tv.setPosition(sbiValue.getPosition());
				tv.setSeverity(MessageBuilderFactory.getMessageBuilder().getMessage(sbiValue.getSeverity().getValueNm()));// sbiValue.getSeverity().getValueNm());
				tv.setSeverityId(sbiValue.getSeverity().getValueId());
				threshold.getThresholdValues().add(tv);
			}
		}
		return threshold;
	}

	private Rule from(SbiKpiRule sbiRule) {
		Rule rule = new Rule();
		rule.setId(sbiRule.getId());
		rule.setName(sbiRule.getName());
		rule.setDefinition(sbiRule.getDefinition());
		rule.setDataSourceId(sbiRule.getDataSourceId());
		if (sbiRule.getSbiKpiPlaceholders() != null) {
			for (SbiKpiPlaceholder sbiKpiPlaceholder : sbiRule.getSbiKpiPlaceholders()) {
				rule.getPlaceholders().add(new Placeholder(sbiKpiPlaceholder.getId(), sbiKpiPlaceholder.getName()));
			}
		}
		if (sbiRule.getSbiKpiRuleOutputs() != null) {
			for (SbiKpiRuleOutput sbiKpiRuleOutput : sbiRule.getSbiKpiRuleOutputs()) {
				rule.getRuleOutputs().add(from(sbiKpiRuleOutput));
			}
		}
		return rule;
	}

	@Override
	public Map<String, List<String>> aliasValidation(final Rule rule) {
		return executeOnTransaction(new IExecuteOnTransaction<Map<String, List<String>>>() {
			@Override
			public Map<String, List<String>> execute(Session session) throws Exception {
				Map<String, List<String>> invalidAlias = new HashMap<>();
				for (RuleOutput ruleOutput : rule.getRuleOutputs()) {
					validateRuleOutput(session, ruleOutput.getAliasId(), ruleOutput.getAlias(), MEASURE.equals(ruleOutput.getType().getValueCd()),
							rule.getId(), invalidAlias);
				}
				return invalidAlias;
			}
		});
	}

	private void validateRuleOutput(Session session, Integer aliasId, String aliasName, boolean isMeasure, Integer ruleId,
			Map<String, List<String>> invalidAlias) {
		// Looking for a RuleOutput with same alias name or alias id
		Criteria c = session.createCriteria(SbiKpiRuleOutput.class).createAlias("sbiKpiAlias", "sbiKpiAlias").createAlias("sbiKpiRule", "sbiKpiRule")
				.setMaxResults(1);
		if (ruleId != null) {
			c.add(Restrictions.ne("sbiKpiRule.id", ruleId));
		}
		if (aliasName != null) {
			c.add(Restrictions.eq("sbiKpiAlias.name", aliasName));
		} else {
			c.add(Restrictions.eq("sbiKpiAlias.id", aliasId));
		}
		if (isMeasure && c.uniqueResult() != null) {
			// This alias is already used
			addAliasToErrorMap(invalidAlias, NEW_KPI_ALIAS_NOT_AVAILABLE_AS_MEASURE, aliasName);
		} else if (c.createAlias("type", "type").add(Restrictions.eq("type.valueCd", MEASURE)).uniqueResult() != null) {
			// This alias is already used as measure
			addAliasToErrorMap(invalidAlias, NEW_KPI_ALIAS_NOT_AVAILABLE, aliasName);
		}
	}

	private void addAliasToErrorMap(Map<String, List<String>> errorMap, String errorKey, String alias) {
		if (errorMap.get(errorKey) == null) {
			errorMap.put(errorKey, new ArrayList<String>());
		}
		errorMap.get(errorKey).add(alias);
	}

	private void ruleValidationForInsert(Session session, Rule rule) throws SpagoBIException {
		checkRule(rule);
		boolean hasMeasure = false;
		Map<String, List<String>> invalidAlias = new HashMap<>();
		for (RuleOutput ruleOutput : rule.getRuleOutputs()) {
			if (ruleOutput.getAliasId() == null && ruleOutput.getAlias() == null) {
				throw new SpagoBIDOAException("RuleOutput Alias is mandatory");
			}
			if (ruleOutput.getType().getValueId() == null) {
				throw new SpagoBIDOAException("RuleOutput Type is mandatory");
			}
			if (MEASURE.equals(ruleOutput.getType().getValueCd())) {
				hasMeasure = true;
				validateRuleOutput(session, ruleOutput.getAliasId(), ruleOutput.getAlias(), true, rule.getId(), invalidAlias);
			} else {
				validateRuleOutput(session, ruleOutput.getAliasId(), ruleOutput.getAlias(), false, rule.getId(), invalidAlias);
			}
		}
		if (!hasMeasure) {
			throw new SpagoBIDOAException("Rule must contain at least one measure");
		}
		if (!invalidAlias.isEmpty()) {
			Entry<String, List<String>> firstError = invalidAlias.entrySet().iterator().next();
			throw new SpagoBIDOAException(MessageFormat.format(message.getMessage(firstError.getKey()), firstError.getValue()));
		}
	}

	private void ruleValidationForUpdate(Session session, Rule rule) throws SpagoBIException {
		checkRule(rule);
		Map<String, List<String>> invalidAlias = new HashMap<>();
		boolean hasMeasure = false;
		for (RuleOutput ruleOutput : rule.getRuleOutputs()) {
			if (ruleOutput.getAlias() == null && ruleOutput.getAliasId() == null) {
				throw new SpagoBIDOAException("RuleOutput Alias is mandatory");
			}
			if (ruleOutput.getType().getValueId() == null) {
				throw new SpagoBIDOAException("RuleOutput Type is mandatory");
			}
			// Looking for other RuleOutput (ie different id) with same "alias name" or "alias id"
			Criteria c = session.createCriteria(SbiKpiRuleOutput.class).createAlias("sbiKpiAlias", "sbiKpiAlias").createAlias("sbiKpiRule", "sbiKpiRule")
					.add(Restrictions.ne("sbiKpiRule.id", ruleOutput.getId())).setMaxResults(1);
			if (ruleOutput.getAlias() != null) {
				c.add(Restrictions.eq("sbiKpiAlias.name", ruleOutput.getAlias()));
			} else {
				c.add(Restrictions.eq("sbiKpiAlias.id", ruleOutput.getAliasId()));
			}
			if (MEASURE.equals(ruleOutput.getType().getValueCd())) {
				hasMeasure = true;
				if (c.uniqueResult() != null) {
					validateRuleOutput(session, ruleOutput.getAliasId(), ruleOutput.getAlias(), true, rule.getId(), invalidAlias);
				}
			} else {
				validateRuleOutput(session, ruleOutput.getAliasId(), ruleOutput.getAlias(), false, rule.getId(), invalidAlias);
			}
		}
		if (!hasMeasure) {
			throw new SpagoBIDOAException("Rule must contain at least one measure");
		}
		if (!invalidAlias.isEmpty()) {
			Entry<String, List<String>> firstError = invalidAlias.entrySet().iterator().next();
			throw new SpagoBIDOAException(MessageFormat.format(message.getMessage(firstError.getKey()), firstError.getValue()));
		}
	}

	private void checkRule(Rule rule) {
		if (rule.getName() == null) {
			throw new SpagoBIDOAException("Rule Name is mandatory");
		}
		if (rule.getDefinition() == null) {
			throw new SpagoBIDOAException("Rule Definition is mandatory");
		}
		if (rule.getDataSourceId() == null) {
			throw new SpagoBIDOAException("Rule Datasource is mandatory");
		}
		if (rule.getRuleOutputs() == null || rule.getRuleOutputs().isEmpty()) {
			throw new SpagoBIDOAException("Rule must contain at least one measure");
		}
	}

}
