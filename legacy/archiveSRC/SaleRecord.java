package Model;

public class SaleRecord {

    private final int    saleId;
    private final String movieId;
    private final int    quantity;
    private final double price;

    public SaleRecord(int saleId, String movieId, int quantity, double price) {
        this.saleId   = saleId;
        this.movieId  = movieId;
        this.quantity = quantity;
        this.price    = price;
    }

    public int    getSaleId()   { return saleId; }
    public String getMovieId()  { return movieId; }
    public int    getQuantity() { return quantity; }
    public double getPrice()    { return price; }
}
