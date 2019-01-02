package co.ledger.wallet.daemon.models;

/**
 * Method defines the type of fee.
 * <p>
 * User: Ting Tu
 * Date: 24-04-2018
 * Time: 16:43
 */
public enum FeeMethod {

    SLOW, NORMAL, FAST;

    public static FeeMethod from(String method) {
        try {
            return FeeMethod.valueOf(method.toUpperCase());
        } catch ( IllegalArgumentException e) {
            throw new IllegalArgumentException("Fee method undefined '" + method + "'");
        }
    }

    public static boolean isValid(String level) {
        try {
            FeeMethod.from(level);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
