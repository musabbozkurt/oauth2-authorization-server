package mb.oauth2authorizationserver.client.webmagic;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import mb.oauth2authorizationserver.client.webmagic.response.Book;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.processor.PageProcessor;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class BookScraper implements PageProcessor {

    private final Site site = Site.me().setRetryTimes(3).setSleepTime(1000);

    @Getter
    private final List<Book> books = new ArrayList<>();

    @Override
    public void process(Page page) {
        var bookNodes = page.getHtml().css("article.product_pod");

        for (int i = 0; i < Math.min(10, bookNodes.nodes().size()); i++) {
            var book = bookNodes.nodes().get(i);

            String title = book.css("h3 a", "title").get();
            String price = book.css(".price_color", "text").get();

            books.add(new Book(title, price));
        }
    }

    @Override
    public Site getSite() {
        return site;
    }
}
