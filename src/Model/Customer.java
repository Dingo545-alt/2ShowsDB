package Model;

public class Customer {
    private int id;
    private String firstName;
    private String lastName;
    private String address;
    private String email;
    private String password;
    private CreditCard creditCard;

    public Customer() {}

    public Customer(int id, String firstName, String lastName, String address,
                    String email, String password, CreditCard creditCard) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.address = address;
        this.email = email;
        this.password = password;
        this.creditCard = new CreditCard();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public CreditCard getCreditCard() { return creditCard; }
    public void setCreditCard(CreditCard creditCard) { this.creditCard = creditCard; }
}
