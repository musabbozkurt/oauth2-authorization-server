package mb.oauth2authorizationserver.client.webmagic;

import mb.oauth2authorizationserver.client.webmagic.response.Book;
import org.junit.jupiter.api.Test;
import us.codecraft.webmagic.Spider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookScraperTest {

    @Test
    void getBooks_ShouldReturnTitleAndPrice_WhenScrapingBookSite() {
        // Arrange
        BookScraper scraper = new BookScraper();

        // Act
        Spider.create(scraper)
                .addUrl("https://books.toscrape.com/")
                .thread(1)
                .run();

        List<Book> books = scraper.getBooks();

        // Assertions
        assertFalse(books.isEmpty(), "Expected to scrape at least one book.");
        assertTrue(books.size() <= 10, "Should not scrape more than 10 books.");
        for (Book book : books) {
            assertNotNull(book.title(), "Book title should not be null.");
            assertFalse(book.title().isBlank(), "Book title should not be blank.");
            assertNotNull(book.price(), "Book price should not be null.");
            assertTrue(book.price().matches("£?\\d+(\\.\\d{2})?"), "Book price format seems invalid: " + book.price());
        }
    }

    @Test
    void getBooks_ShouldParseAndSortPricesDescending_WhenScrapingBookSite() {
        // Arrange
        BookScraper scraper = new BookScraper();

        // Act
        Spider.create(scraper)
                .addUrl("https://books.toscrape.com/")
                .thread(1)
                .run();

        List<Book> books = scraper.getBooks();
        List<Double> prices = books.stream()
                .map(Book::price)
                .map(p -> p.replace("£", ""))
                .map(Double::parseDouble)
                .toList();

        List<Double> sorted = prices.stream()
                .sorted((a, b) -> Double.compare(b, a))
                .toList();

        // Assertions
        assertFalse(books.isEmpty(), "No books were scraped.");
        assertEquals(sorted, prices.stream().sorted((a, b) -> Double.compare(b, a)).toList(), "Prices are not in descending order after sorting.");
    }

    @Test
    void getBooks_ShouldContainExpectedKeywordsInTitles_WhenScrapingBookSite() {
        // Arrange
        BookScraper scraper = new BookScraper();

        // Act
        Spider.create(scraper)
                .addUrl("https://books.toscrape.com/")
                .thread(1)
                .run();

        List<Book> books = scraper.getBooks();

        boolean foundKeyword = books.stream()
                .map(Book::title)
                .anyMatch(title -> title.toLowerCase().matches(".*\\b(book|story|novel|guide|life)\\b.*"));

        // Assertions
        assertFalse(books.isEmpty(), "No books were scraped.");
        assertTrue(foundKeyword, "No book titles contain expected keywords.");
    }

    @Test
    void getBooks_ShouldHaveStableBookCount_WhenScrapedMultipleTimes() {
        // Arrange
        BookScraper scraper1 = new BookScraper();
        BookScraper scraper2 = new BookScraper();

        // Act
        Spider.create(scraper1)
                .addUrl("https://books.toscrape.com/")
                .thread(1)
                .run();

        Spider.create(scraper2)
                .addUrl("https://books.toscrape.com/")
                .thread(1)
                .run();

        int count1 = scraper1.getBooks().size();
        int count2 = scraper2.getBooks().size();

        // Assertions
        assertEquals(count1, count2, "Book count is not stable between two runs.");
    }
}
