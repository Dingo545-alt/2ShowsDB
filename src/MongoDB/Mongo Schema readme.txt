moviedb -- MongoDB Schema
Six collections: movies, stars, directors, customers, sales, employees
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
  ],
  "poster": {
    "path": "/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg",
    "sizes": {
      "w342":     "https://image.tmdb.org/t/p/w342/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg",
      "original": "https://image.tmdb.org/t/p/original/q6y0Go1tsGEsmtFryDOJo3dEmqu.jpg"
    }
  }
}

- rating and vote_count are null for unrated movies
- genres is alphabetically sorted
- stars ordered by career movie count DESC, then name ASC
- poster is null when the source has no poster image; path is the TMDB-relative
  path (kept for reference/debugging), sizes holds pre-resolved full URLs for
  the sizes the app actually uses (w342 for list thumbnails, original for the
  detail page) so the app never has to know TMDB's image base URL / size scheme


stars
----------------------------------------------------------------
{
  "_id":  "nm0000151",
  "name": "Tim Robbins",
  "dob":  "1958-10-16",
  "photo": {
    "path": "/eOo3bfnyCFbcw0lseYUZDgvhpn0.jpg",
    "sizes": {
      "w185":     "https://image.tmdb.org/t/p/w185/eOo3bfnyCFbcw0lseYUZDgvhpn0.jpg",
      "original": "https://image.tmdb.org/t/p/original/eOo3bfnyCFbcw0lseYUZDgvhpn0.jpg"
    }
  },
  "movies": [
    { "id": "tt0245429", "title": "Mystic River",             "year": 2003 },
    { "id": "tt0111161", "title": "The Shawshank Redemption", "year": 1994 }
  ]
}

- dob is an ISO 8601 date string ("YYYY-MM-DD"); null when unknown
- photo is null when the source has no profile image; path is the TMDB-relative
  path (kept for reference/debugging), sizes holds pre-resolved full URLs for
  the sizes the app actually uses (w185 for list/detail thumbnails, original
  for a full-size view) so the app never has to know TMDB's image base URL /
  size scheme
- movies ordered by year DESC, then title ASC


directors
----------------------------------------------------------------
{
  "_id":  "nm0000399",
  "name": "Frank Darabont",
  "dob":  "1959-01-28",
  "photo": {
    "path": "/lNqRT7dfNoLuTgOG3JsPSbYbUnV.jpg",
    "sizes": {
      "w185":     "https://image.tmdb.org/t/p/w185/lNqRT7dfNoLuTgOG3JsPSbYbUnV.jpg",
      "original": "https://image.tmdb.org/t/p/original/lNqRT7dfNoLuTgOG3JsPSbYbUnV.jpg"
    }
  },
  "movies": [
    { "id": "tt0111161", "title": "The Shawshank Redemption", "year": 1994 }
  ]
}

- structurally identical to stars (same dob/photo/movies conventions above);
  a separate collection because a person can be a star, a director, or both,
  each with their own page
- movies.director on the movie document itself stays a plain name string —
  it does not reference directors._id


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