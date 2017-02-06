package experiment.mockserver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.cloudant.client.api.ClientBuilder;
import com.cloudant.client.api.CloudantClient;
import com.cloudant.client.api.Database;
import com.cloudant.client.api.model.Params;
import com.cloudant.client.api.model.Response;
import com.cloudant.client.org.lightcouch.DocumentConflictException;
import com.cloudant.client.org.lightcouch.NoDocumentException;
import com.cloudant.test.main.RequiresCouch;
import com.cloudant.tests.Bar;
import com.cloudant.tests.Foo;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DocumentsCRUDTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(new WireMockConfiguration().dynamicPort().dynamicHttpsPort().usingFilesUnderDirectory("cloudant-client/src/test/resources/testfixtures").extensions(new ResponseTemplateTransformer(false)));
    // TODO custom client which overrides getBaseUri?
    public CloudantClient account;
    public Database db;

    @Before
    public void setUp() throws IOException {
        account = ClientBuilder.url(new URL("http://localhost:"+wireMockRule.port())).build();
        List<StubMapping> mappings = wireMockRule.getStubMappings();

        db = account.database("db", false);
    }

    // Find

    @Test
    public void findById() {
        Response response = db.save(new Foo());
        Foo foo = db.find(Foo.class, response.getId());
        assertNotNull(foo);
    }

    @Test
    public void findByIdAndRev() {
        Response response = db.save(new Foo());
        Foo foo = db.find(Foo.class, response.getId(), response.getRev());
        assertNotNull(foo);
    }

    @Test
    public void findByIdContainSlash() {
        Response response = db.save(new Foo(generateUUID() + "/" + generateUUID()));
        Foo foo = db.find(Foo.class, response.getId());
        assertNotNull(foo);

        Foo foo2 = db.find(Foo.class, response.getId(), response.getRev());
        assertNotNull(foo2);
    }

    @Test
    public void findJsonObject() {
        Response response = db.save(new Foo());
        JsonObject jsonObject = db.find(JsonObject.class, response.getId());
        assertNotNull(jsonObject);
    }

    @Test
    @Category(RequiresCouch.class)
    public void findAny() {
        String uri = account.getBaseUri() + "_stats";
        JsonObject jsonObject = db.findAny(JsonObject.class, uri);
        assertNotNull(jsonObject);
    }

    @Test
    public void findInputstream() throws IOException {
        Response response = db.save(new Foo());
        InputStream inputStream = db.find(response.getId());
        assertTrue(inputStream.read() != -1);
        inputStream.close();
    }

    @Test
    public void findWithParams() {
        Response response = db.save(new Foo());
        Foo foo = db.find(Foo.class, response.getId(), new Params().revsInfo());
        assertNotNull(foo);
    }

    @Test(expected = IllegalArgumentException.class)
    public void findWithInvalidId_throwsIllegalArgumentException() {
        db.find(Foo.class, "");
    }

    // TODO needs server
    @Test(expected = NoDocumentException.class)
    public void findWithUnknownId_throwsNoDocumentException() {
        db.find(Foo.class, generateUUID());
    }

    @Test
    public void contains() {
        Response response = db.save(new Foo());
        boolean found = db.contains(response.getId());
        assertTrue(found);

        found = db.contains(generateUUID());
        assertFalse(found);
    }

    // Save

    @Test
    public void savePOJO() {
        db.save(new Foo());
    }

    @Test
    public void saveMap() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("_id", generateUUID());
        map.put("field1", "value1");
        db.save(map);
    }

    @Test
    public void saveJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("_id", generateUUID());
        json.add("json-array", new JsonArray());
        db.save(json);
    }

    @Test
    public void saveWithIdContainSlash() {
        String idWithSlash = "a/b/" + generateUUID();
        Response response = db.save(new Foo(idWithSlash));
        assertEquals(idWithSlash, response.getId());
    }

    @Test
    public void saveObjectPost() {
        // database generated id will be assigned
        Response response = db.post(new Foo());
        assertNotNull(response.getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void saveInvalidObject_throwsIllegalArgumentException() {
        db.save(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void saveNewDocWithRevision_throwsIllegalArgumentException() {
        Bar bar = new Bar();
        bar.setRevision("unkown");
        db.save(bar);
    }

    // TODO needs server
    @Test(expected = DocumentConflictException.class)
    public void saveDocWithDuplicateId_throwsDocumentConflictException() {
        String id = generateUUID();
        db.save(new Foo(id));
        db.save(new Foo(id));
    }

    // Update

    @Test
    public void update() {
        Response response = db.save(new Foo());
        Foo foo = db.find(Foo.class, response.getId());
        db.update(foo);
    }

    @Test
    public void updateWithIdContainSlash() {
        String idWithSlash = "a/" + generateUUID();
        Response response = db.save(new Bar(idWithSlash));

        Bar bar = db.find(Bar.class, response.getId());
        Response responseUpdate = db.update(bar);
        assertEquals(idWithSlash, responseUpdate.getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void updateWithoutIdAndRev_throwsIllegalArgumentException() {
        db.update(new Foo());
    }

    // TODO needs server
    @Test(expected = DocumentConflictException.class)
    public void updateWithInvalidRev_throwsDocumentConflictException() {
        Response response = db.save(new Foo());
        Foo foo = db.find(Foo.class, response.getId());
        db.update(foo);
        db.update(foo);
    }

    // Delete

    @Test
    public void deleteObject() {
        Response response = db.save(new Foo());
        Foo foo = db.find(Foo.class, response.getId());
        db.remove(foo);
    }

    @Test
    public void deleteByIdAndRevValues() {
        Response response = db.save(new Foo());
        db.remove(response.getId(), response.getRev());
    }

    @Test
    public void deleteByIdContainSlash() {
        String idWithSlash = "a/" + generateUUID();
        Response response = db.save(new Bar(idWithSlash));

        Response responseRemove = db.remove(response.getId(), response.getRev());
        assertEquals(idWithSlash, responseRemove.getId());
    }

    @Test
    public void testCedilla() {
        Foo f = new Foo();
        f.setTitle("François");
        db.save(f);
    }

    // Helper

    private static String generateUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
