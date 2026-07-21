moviedb -- MongoDB Schema
Five collections: movies, stars, customers, sales, employees
================================================================


movies
----------------------------------------------------------------
{
  "_id":        "tt0111161",
  "title":      "The Shawshank Redemption",
  "year":       1994,
  "director":   "Frank Darabont",
  "price":      14.99,
  "rating":     9.3,
  "vote_count": 2900000,
  "genres": ["Drama"],
  "stars": [
    { "id": "nm0000209", "name": "Morgan Freeman" },
    { "id": "nm0000151", "name": "Tim Robbins"    }
  ]
}

- rating and vote_count are null for unrated movies
- genres is alphabetically sorted
- stars ordered by career movie count DESC, then name ASC


stars
----------------------------------------------------------------
{
  "_id":        "nm0000151",
  "name":       "Tim Robbins",
  "birth_year": 1958,
  "movies": [
    { "id": "tt0245429", "title": "Mystic River",             "year": 2003 },
    { "id": "tt0111161", "title": "The Shawshank Redemption", "year": 1994 }
  ]
}

- birth_year is null when unknown
- movies ordered by year DESC, then title ASC


customers
----------------------------------------------------------------
{
  "_id":        1,
  "first_name": "Jane",
  "last_name":  "Doe",
  "email":      "jane@example.com",
  "password":   "1234567890encryptedHash...",
  "address":    "123 Main St, Los Angeles, CA",
  "credit_card": {
    "id":         "4111111111111111",
    "first_name": "Jane",
    "last_name":  "Doe",
    "expiration": "2027-06-30"
  }
}


sales
----------------------------------------------------------------
{
  "_id":           42,
  "customer_id":   1,
  "movie_id":      "tt0111161",
  "sale_date":     "2024-03-15",
  "quantity":      2,
  "price_at_sale": 14.99
}

- customer_id references customers._id
- movie_id     references movies._id
- price_at_sale is a snapshot of the price at time of purchase


employees
----------------------------------------------------------------
{
  "_id":      "admin@example.com",
  "password": "1234567890encryptedHash...",
  "fullname": "Admin User"
}