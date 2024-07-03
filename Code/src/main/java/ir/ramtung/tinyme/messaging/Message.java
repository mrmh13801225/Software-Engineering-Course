package ir.ramtung.tinyme.messaging;

public class Message {
    public static final String INVALID_ORDER_ID = "Invalid order ID";
    public static final String ORDER_QUANTITY_NOT_POSITIVE = "Order quantity is not-positive";
    public static final String ORDER_PRICE_NOT_POSITIVE = "Order price is not-positive";
    public static final String ORDER_MIN_EXEC_QUANTITY_NOT_POSITIVE = "Order minimum execution quantity is not-positive";
    public static final String SO_BIG_ORDER_MIN_EXEC_QUANTITY = "Order minimum execution quantity is bigger than order quantity";
    public static final String MIN_EXEC_QUANTITY_CONDITION_NOT_MET = "Order minimum execution quantity haven't met";
    public static final String CANNOT_CHANGE_MIN_EXEC_QUANTITY_WHILE_UPDATING_REQUEST =
            "cant change min execution quantity while updating the order";
    public static final String UNKNOWN_SECURITY_ISIN = "Unknown security ISIN";
    public static final String ORDER_ID_NOT_FOUND = "Order ID not found in the order book";
    public static final String INVALID_PEAK_SIZE = "Iceberg order peak size is out of range";
    public static final String CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER = "Cannot specify peak size for a non-iceberg order";
    public static final String UNKNOWN_BROKER_ID = "Unknown broker ID";
    public static final String UNKNOWN_SHAREHOLDER_ID = "Unknown shareholder ID";
    public static final String BUYER_HAS_NOT_ENOUGH_CREDIT = "Buyer has not enough credit";
    public static final String QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE = "Quantity is not a multiple of security lot size";
    public static final String PRICE_NOT_MULTIPLE_OF_TICK_SIZE = "Price is not a multiple of security tick size";
    public static final String SELLER_HAS_NOT_ENOUGH_POSITIONS = "Seller has not enough positions";
    public static final String STOP_LIMIT_ORDER_CANNOT_BE_ICEBERG = "Stop limit order cannot be iceberg";
    public static final String INVALID_STOP_PRICE = "Invalid stop price";
    public static final String STOP_LIMIT_ORDER_CANNOT_HAVE_MIN_EXEC = "Stop limit order cannot have minimum execution quantity";
    public static final String CANNOT_CHANGE_STOP_LIMIT_ORDER_FOR_AUCTION_SECURITY = "Cannot change stop limit order for auction security";
    public static final String AUCTION_ORDER_CANNOT_HAVE_MIN_EXEC_QUANTITY = "Auction order cannot have minimum execution quantity";
    public static final String CANNOT_DELETE_STOP_LIMIT_ORDER_IN_AUCTION = "Cannot delete stop limit order in auction";
    public static final String CANNOT_HAVE_STOP_LIMIT_ORDER_IN_AUCTION_SECURITY = "Cannot have stop limit order in auction security";
}
