package com.stellar.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.util.ObjectBuilder;
import com.github.benmanes.caffeine.cache.Cache;
import com.stellar.dto.SpuPageQueryDTO;
import com.stellar.elasticsearch.doc.SpuDocument;
import com.stellar.elasticsearch.service.SearchSuggestService;
import com.stellar.elasticsearch.service.SpuSearchService;
import com.stellar.elasticsearch.service.SynonymEngine;
import com.stellar.mapper.SpuMapper;
import com.stellar.vo.SearchResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ElasticsearchMigrationTest {

    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final SpuMapper mapper = mock(SpuMapper.class);
    private final SynonymEngine synonyms = mock(SynonymEngine.class);
    private SpuSearchService service;
    private SearchHits<SpuDocument> hits;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new SpuSearchService(operations, client, mapper, synonyms);
        ReflectionTestUtils.setField(service, "indexPrefix", "stellar");
        hits = mock(SearchHits.class);
        when(hits.iterator()).thenAnswer(invocation -> Collections.emptyIterator());
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(NativeQuery.class), eq(SpuDocument.class))).thenReturn(hits);
        when(synonyms.expand(anyString())).thenAnswer(invocation -> List.of(invocation.getArgument(0, String.class)));
    }

    @Test
    void knnPreservesCandidateCountFiltersAndCosineThreshold() throws Exception {
        SpuPageQueryDTO dto = keyword("大屏手机");
        dto.setCategoryId(7L);
        dto.setStatus(1);
        dto.setIsNew(1);
        dto.setIsHot(0);
        dto.setPriceFrom(BigDecimal.valueOf(50));
        dto.setPriceTo(BigDecimal.valueOf(500));
        stubVectors(0.80, 0.60, 0.59);

        SearchResultVO result = service.searchWithHighlight(dto);

        assertEquals(2L, result.getTotal());
        verify(mapper).listByIds(List.of(1L, 2L));
        SearchRequest request = capturedKnnRequest();
        assertEquals(List.of("stellar_spu"), request.index());
        assertEquals(100, request.size());
        var knn = request.knn().get(0);
        assertEquals("nameVec", knn.field());
        assertEquals(100L, knn.k());
        assertEquals(500L, knn.numCandidates());
        assertEquals(List.of(1.0f, 0.0f), knn.queryVector());
        assertEquals(5, knn.filter().size());
        assertTrue(knn.filter().stream().anyMatch(q -> q.isTerm()
                && q.term().field().equals("categoryId") && q.term().value().longValue() == 7));
        assertTrue(knn.filter().stream().anyMatch(q -> q.isRange()
                && q.range().field().equals("minPrice")
                && q.range().gte().to(Double.class).equals(50.0)
                && q.range().lte().to(Double.class).equals(500.0)));
        assertTrue(request.toString().contains("\"num_candidates\":500"));
    }

    @Test
    void bm25PreservesExactNameBoostAndStrictSynonyms() throws Exception {
        SpuPageQueryDTO dto = keyword("手机膜");
        when(synonyms.expand("手机膜")).thenReturn(List.of("手机膜", "钢化膜"));
        stubVectors();

        service.searchWithHighlight(dto);

        NativeQuery query = capturedTextQuery();
        var bool = query.getQuery().bool();
        assertEquals("1", bool.minimumShouldMatch());
        assertEquals(5, bool.should().size());
        var exact = bool.should().get(0).term();
        assertEquals("name.keyword", exact.field());
        assertEquals("手机膜", exact.value().stringValue());
        assertEquals(100.0f, exact.boost());
        var multiMatch = bool.should().get(2).multiMatch();
        assertEquals(List.of("name^3", "subTitle"), multiMatch.fields());
        assertEquals("and", multiMatch.operator().jsonValue());
        assertEquals("钢化膜", bool.should().get(3).matchPhrase().query());
        assertNotNull(query.getHighlightQuery());
    }

    @Test
    void latinNoiseDoesNotReceiveSemanticOnlyResults() throws Exception {
        SpuPageQueryDTO dto = keyword("qwertyuiop");
        stubVectors(0.95);

        assertEquals(0L, service.searchWithHighlight(dto).getTotal());
        verify(mapper, never()).listByIds(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void vectorFailureRetainsBm25ResultsAndHighlights() throws Exception {
        SpuPageQueryDTO dto = keyword("手机");
        SpuDocument document = new SpuDocument();
        document.setId(9L);
        SearchHit<SpuDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(document);
        when(hit.getHighlightFields()).thenReturn(Map.of("name", List.of("<em class='hl'>手机</em>")));
        when(hits.iterator()).thenAnswer(invocation -> List.of(hit).iterator());
        when(client.search(any(Function.class), eq(Void.class)))
                .thenThrow(new java.io.IOException("vector unavailable"));

        SearchResultVO result = service.searchWithHighlight(dto);

        assertEquals(1L, result.getTotal());
        assertEquals(List.of("<em class='hl'>手机</em>"), result.getHighlights().get(9L));
        verify(mapper).listByIds(List.of(9L));
    }

    @Test
    void typedAggregationsPreserveCategoryAndPriceBuckets() {
        var categories = Aggregate.of(a -> a.lterms(t -> t
                .sumOtherDocCount(0L).docCountErrorUpperBound(0L)
                .buckets(b -> b.array(List.of(
                        co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket.of(
                                bucket -> bucket.key(7).docCount(3)))))));
        var prices = Aggregate.of(a -> a.range(r -> r.buckets(b -> b.array(List.of(
                co.elastic.clients.elasticsearch._types.aggregations.RangeBucket.of(
                        bucket -> bucket.key("50.0-100.0").from(50.0).to(100.0).docCount(3)))))));
        doReturn(new ElasticsearchAggregations(Map.of("by_category", categories, "by_price", prices)))
                .when(hits).getAggregations();
        when(hits.getTotalHits()).thenReturn(3L);

        SearchResultVO result = service.searchWithHighlight(new SpuPageQueryDTO());

        assertEquals(3L, result.getTotal());
        assertEquals("7", result.getAggregations().getCategories().get(0).getKey());
        assertEquals("50.0-100.0", result.getAggregations().getPriceRanges().get(0).getKey());
        verifyNoInteractions(mapper, client);
        assertEquals(2, capturedTextQuery().getAggregations().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void autocompleteUsesPhrasePrefixAndDeduplicatesNames() {
        SpuDocument document = new SpuDocument();
        document.setName("手机膜");
        SearchHit<SpuDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(document);
        when(hits.iterator()).thenAnswer(invocation -> List.of(hit, hit).iterator());
        when(synonyms.allTerms()).thenReturn(java.util.Set.of("手机膜", "手机壳"));
        SearchSuggestService suggest = new SearchSuggestService(operations, synonyms);

        assertEquals(List.of("手机膜", "手机壳"), suggest.suggest(" 手机 ").getCompletions());
        assertEquals("手机", capturedTextQuery().getQuery().bool().must().get(0).matchPhrasePrefix().query());
    }

    @Test
    void correctionUsesFuzzinessAndRejectsShortInput() {
        SearchSuggestService suggest = new SearchSuggestService(operations, synonyms);
        assertNull(suggest.correct("ab"));
        assertTrue(suggest.suggest(" ").getCompletions().isEmpty());
        verifyNoInteractions(operations);

        assertNull(suggest.correct("iphoen"));
        var multiMatch = capturedTextQuery().getQuery().bool().must().get(0).multiMatch();
        assertEquals("AUTO", multiMatch.fuzziness());
        assertEquals(List.of("name", "subTitle"), multiMatch.fields());
    }

    @Test
    void newIndexContainsIndexedVectorsAndExactNameField() throws Exception {
        var index = mock(org.springframework.data.elasticsearch.core.IndexOperations.class);
        var restClient = mock(org.elasticsearch.client.RestClient.class);
        when(operations.indexOps(SpuDocument.class)).thenReturn(index);
        when(index.createMapping(SpuDocument.class)).thenReturn(
                org.springframework.data.elasticsearch.core.document.Document.parse(
                        "{\"properties\":{\"name\":{\"type\":\"text\",\"analyzer\":\"ik_max_word\"}}}"));
        var config = new com.stellar.elasticsearch.config.ElasticsearchConfig(operations, restClient);

        config.ensureIndex(null);

        ArgumentCaptor<org.elasticsearch.client.Request> requests =
                ArgumentCaptor.forClass(org.elasticsearch.client.Request.class);
        verify(restClient, times(2)).performRequest(requests.capture());
        var create = requests.getAllValues().get(0);
        assertEquals("/stellar_spu", create.getEndpoint());
        assertEquals("PUT", create.getMethod());
        var properties = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                org.apache.http.util.EntityUtils.toString(create.getEntity())).path("mappings").path("properties");
        assertEquals("ik_max_word", properties.path("name").path("analyzer").asText());
        assertEquals("keyword", properties.path("name").path("fields").path("keyword").path("type").asText());
        assertEquals(1536, properties.path("nameVec").path("dims").asInt());
        assertTrue(properties.path("nameVec").path("index").asBoolean());
        assertEquals("cosine", properties.path("nameVec").path("similarity").asText());
    }

    @Test
    void existingIndexAlsoReceivesExactNameMapping() throws Exception {
        var index = mock(org.springframework.data.elasticsearch.core.IndexOperations.class);
        var restClient = mock(org.elasticsearch.client.RestClient.class);
        when(operations.indexOps(SpuDocument.class)).thenReturn(index);
        when(index.exists()).thenReturn(true);
        var config = new com.stellar.elasticsearch.config.ElasticsearchConfig(operations, restClient);

        config.ensureIndex(null);

        ArgumentCaptor<org.elasticsearch.client.Request> requests =
                ArgumentCaptor.forClass(org.elasticsearch.client.Request.class);
        verify(restClient).performRequest(requests.capture());
        verify(index, never()).createMapping(any(Class.class));
        assertEquals("/stellar_spu/_mapping", requests.getValue().getEndpoint());
        var properties = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                org.apache.http.util.EntityUtils.toString(requests.getValue().getEntity())).path("properties");
        assertEquals("keyword", properties.path("name").path("fields").path("keyword").path("type").asText());
        assertTrue(properties.path("nameVec").path("index").asBoolean());
    }

    @SuppressWarnings("unchecked")
    private SpuPageQueryDTO keyword(String text) {
        Cache<String, double[]> cache = (Cache<String, double[]>) ReflectionTestUtils.getField(service, "embedCache");
        cache.put(text, new double[]{1.0, 0.0});
        SpuPageQueryDTO dto = new SpuPageQueryDTO();
        dto.setName(text);
        return dto;
    }

    @SuppressWarnings("unchecked")
    private void stubVectors(double... scores) throws Exception {
        var builder = new SearchResponse.Builder<Void>()
                .took(1).timedOut(false).shards(s -> s.total(1).successful(1).failed(0));
        var vectorHits = new java.util.ArrayList<co.elastic.clients.elasticsearch.core.search.Hit<Void>>();
        for (int i = 0; i < scores.length; i++) {
            vectorHits.add(new co.elastic.clients.elasticsearch.core.search.Hit.Builder<Void>()
                    .index("stellar_spu").id(String.valueOf(i + 1)).score(scores[i]).build());
        }
        when(client.search(any(Function.class), eq(Void.class)))
                .thenReturn(builder.hits(h -> h.hits(vectorHits)).build());
    }

    @SuppressWarnings("unchecked")
    private SearchRequest capturedKnnRequest() throws Exception {
        ArgumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>> captor =
                ArgumentCaptor.forClass(Function.class);
        verify(client).search(captor.capture(), eq(Void.class));
        return captor.getValue().apply(new SearchRequest.Builder()).build();
    }

    private NativeQuery capturedTextQuery() {
        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captor.capture(), eq(SpuDocument.class));
        return captor.getValue();
    }
}
