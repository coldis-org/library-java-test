package org.coldis.library.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.collections4.CollectionUtils;
import org.coldis.library.helper.ReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test helper.
 */
public class TestHelper {

	/**
	 * Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(TestHelper.class);

	/**
	 * Short wait time (milliseconds).
	 */
	public static final Integer SHORT_WAIT = 500;

	/**
	 * Regular wait time (milliseconds).
	 */
	public static final Integer REGULAR_WAIT = 25 * 100;

	/**
	 * Long wait time (milliseconds).
	 */
	public static final Integer LONG_WAIT = 11 * 1000;

	/**
	 * Long wait time (milliseconds).
	 */
	public static final Integer VERY_LONG_WAIT = 29 * 1000;

	/**
	 * Waits until variable is valid.
	 *
	 * @param  <Type>             The variable type.
	 * @param  variableSupplier   Variable supplier function.
	 * @param  validVariableState The variable valid state verification.
	 * @param  maxWait            Milliseconds to wait until valid state is met.
	 * @param  poll               Milliseconds between validity verification.
	 * @param  exceptionsToIgnore Exceptions to be ignored on validity verification.
	 * @return                    If a valid variable state has been met within the
	 *                            maximum wait period.
	 * @throws Exception          If the validity verification throws a non
	 *                                ignorable exception.
	 */
	@SafeVarargs
	public static <Type> Boolean waitUntilValid(final Supplier<Type> variableSupplier,
			final Predicate<Type> validVariableState, final Integer maxWait, final Integer poll,
			final Class<? extends Throwable>... exceptionsToIgnore) throws Exception {
		// Valid state is not considered met by default.
		Boolean validStateMet = false;
		// Validation start time stamp.
		final Long startTimestamp = System.currentTimeMillis();
		// Until wait time is not reached.
		for (Long currentTimestamp = System.currentTimeMillis(); (startTimestamp
				+ maxWait) > currentTimestamp; currentTimestamp = System.currentTimeMillis()) {
			// If the variable state is valid.
			try {
				if (validVariableState.test(variableSupplier.get())) {
					// Valid state has been met.
					validStateMet = true;
					break;
				}
			}
			// If the variable state cannot be tested.
			catch (final Throwable throwable) {
				// If the exception is not to be ignored.
				if ((exceptionsToIgnore == null) || !Arrays.asList(exceptionsToIgnore).stream()
						.anyMatch(exception -> exception.isAssignableFrom(throwable.getClass()))) {
					// Throws the exception and stops the wait.
					throw throwable;
				}
			}
			// Waits a bit.
			Thread.sleep(poll);
		}
		// Returns if valid state has been met.
		return validStateMet;
	}

	/**
	 * Creates incomplete objects.
	 *
	 * @param  <Type>            Type.
	 * @param  baseObject        Base object to be cloned with incomplete data.
	 * @param  cloneFunction     Clone function.
	 * @param  attributesToUnset Attributes to individually unset from base object.
	 * @return                   Various clones of the base object with missing
	 *                           attributes.
	 */
	public static <Type> Collection<Type> createIncompleteObjects(final Type baseObject,
			final Function<Type, Type> cloneFunction, final Collection<String> attributesToUnset) {
		// Creates the incomplete objects list.
		final List<Type> incompleteObjects = new ArrayList<>();
		// If both base object and attributes are given.
		if ((baseObject != null) && !CollectionUtils.isEmpty(attributesToUnset)) {
			// For each attribute to unset.
			for (final String attributeToUnset : attributesToUnset) {
				// Clones the object and adds it to the list.
				final Type incompleteObject = cloneFunction.apply(baseObject);
				incompleteObjects.add(incompleteObject);
				// Sets the attribute to null.
				ReflectionHelper.setAttribute(incompleteObject, attributeToUnset, null);
			}
		}
		// Returns the incomplete objects list.
		return incompleteObjects;
	}

}
