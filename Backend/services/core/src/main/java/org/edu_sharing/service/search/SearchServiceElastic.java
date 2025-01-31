package org.edu_sharing.service.search;

import com.google.gson.Gson;
import net.sourceforge.cardme.engine.VCardEngine;
import net.sourceforge.cardme.vcard.VCard;
import net.sourceforge.cardme.vcard.types.ExtendedType;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.security.permissions.PermissionReference;
import org.alfresco.repo.security.permissions.impl.model.PermissionModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.StoreRef;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.edu_sharing.alfresco.lightbend.LightbendConfigLoader;
import org.edu_sharing.alfresco.repository.server.authentication.Context;
import org.edu_sharing.alfresco.workspace_administration.NodeServiceInterceptor;
import org.edu_sharing.alfrescocontext.gate.AlfAppContextGate;
import org.edu_sharing.metadataset.v2.MetadataQuery;
import org.edu_sharing.metadataset.v2.MetadataReader;
import org.edu_sharing.metadataset.v2.MetadataSet;
import org.edu_sharing.metadataset.v2.tools.MetadataElasticSearchHelper;
import org.edu_sharing.repackaged.elasticsearch.org.apache.http.HttpHost;
import org.edu_sharing.repository.client.tools.CCConstants;
import org.edu_sharing.repository.client.tools.metadata.ValueTool;
import org.edu_sharing.repository.server.AuthenticationToolAPI;
import org.edu_sharing.repository.server.SearchResultNodeRef;
import org.edu_sharing.repository.server.tools.ApplicationInfoList;
import org.edu_sharing.repository.server.tools.LogTime;
import org.edu_sharing.repository.server.tools.URLTool;
import org.edu_sharing.service.authority.AuthorityServiceHelper;
import org.edu_sharing.service.model.NodeRef;
import org.edu_sharing.service.model.NodeRefImpl;
import org.edu_sharing.service.permission.PermissionServiceHelper;
import org.edu_sharing.service.search.model.SearchToken;
import org.edu_sharing.service.search.model.SearchVCard;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.search.*;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.ParsedCardinality;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.collapse.CollapseBuilder;
import org.elasticsearch.search.sort.ScoreSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xcontent.*;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SearchServiceElastic extends SearchServiceImpl {
    static RestHighLevelClient client;
    public SearchServiceElastic(String applicationId) {
        super(applicationId);
    }

    Logger logger = Logger.getLogger(SearchServiceElastic.class);

    ApplicationContext alfApplicationContext = AlfAppContextGate.getApplicationContext();

    ServiceRegistry serviceRegistry = (ServiceRegistry) alfApplicationContext.getBean(ServiceRegistry.SERVICE_REGISTRY);

    PermissionModel permissionModel = (PermissionModel)alfApplicationContext.getBean("permissionsModelDAO");

    public static HttpHost[] getConfiguredHosts() {
        List<HttpHost> hosts=null;
        try {
            List<String> servers= LightbendConfigLoader.get().getStringList("elasticsearch.servers");
            hosts=new ArrayList<>();
            for(String server : servers) {
                hosts.add(new HttpHost(server.split(":")[0],Integer.parseInt(server.split(":")[1])));
            }
        }catch(Throwable t) {
        }
        return hosts.toArray(new HttpHost[0]);
    }

    public SearchResultNodeRefElastic searchDSL(String dsl) throws Throwable {
        checkClient();
        SearchRequest searchRequest = new SearchRequest("workspace");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchModule searchModule = new SearchModule(Settings.EMPTY, false, Collections.emptyList());
        try (XContentParser parser = XContentFactory.xContent(XContentType.JSON).createParser(new NamedXContentRegistry(searchModule
                .getNamedXContents()), DeprecationHandler.IGNORE_DEPRECATIONS, dsl)) {
            searchSourceBuilder.parseXContent(parser);
        }
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, getRequestOptions());

        SearchResultNodeRefElastic sr = new SearchResultNodeRefElastic();
        List<NodeRef> data = new ArrayList<>();
        sr.setData(data);
        sr.setElasticResponse(searchResponse);
        SearchHits hits = searchResponse.getHits();
        logger.info("result count: "+hits.getTotalHits());
        sr.setNodeCount((int) hits.getTotalHits().value);
        Set<String> authorities = getUserAuthorities();
        String user = serviceRegistry.getAuthenticationService().getCurrentUserName();
        for (SearchHit hit : hits) {
            data.add(transformSearchHit(authorities, user, hit, false));
        }
        return sr;
    }

    private RequestOptions getRequestOptions() {
        RequestOptions.Builder b = RequestOptions.DEFAULT.toBuilder();
        // add trace headers to elastic request
        if (Context.getCurrentInstance() != null) {
            for (Map.Entry<String, String> header : Context.getCurrentInstance().getB3().getX3Headers().entrySet()) {
                b.addHeader(header.getKey(), header.getValue());
            }
        }
        return b.build();
    }

    public BoolQueryBuilder getPermissionsQuery(String field){
        Set<String> authorities = getUserAuthorities();
        return getPermissionsQuery(field,authorities);
    }
    public BoolQueryBuilder getPermissionsQuery(String field, Set<String> authorities){
        BoolQueryBuilder audienceQueryBuilder = QueryBuilders.boolQuery();
        audienceQueryBuilder.minimumShouldMatch(1);
        for (String a : authorities) {
            audienceQueryBuilder.should(QueryBuilders.matchQuery(field, a));
        }
        return audienceQueryBuilder;
    }
    public BoolQueryBuilder getReadPermissionsQuery(){

        if(AuthorityServiceHelper.isAdmin() || AuthenticationUtil.isRunAsUserTheSystemUser()){
            return new BoolQueryBuilder().must(QueryBuilders.matchAllQuery());
        }

        String user = serviceRegistry.getAuthenticationService().getCurrentUserName();
        BoolQueryBuilder audienceQueryBuilder = getPermissionsQuery("permissions.read");
        audienceQueryBuilder.should(QueryBuilders.matchQuery("owner", user));

        //enhance to collection permissions
        MatchQueryBuilder collectionTypeProposal = QueryBuilders.matchQuery("collections.relation.type", "ccm:collection_proposal");
        // @TODO: FIX after DESP-840
        BoolQueryBuilder collectionPermissions = getPermissionsQuery("collections.permissions.read.keyword");
        collectionPermissions.should(QueryBuilders.matchQuery("collections.owner", user));
        collectionPermissions.mustNot(collectionTypeProposal);

        BoolQueryBuilder proposalPermissions = getPermissionsQuery("collections.permissions.Coordinator",getUserAuthorities().stream().filter(a -> !a.equals(CCConstants.AUTHORITY_GROUP_EVERYONE)).collect(Collectors.toSet()));
        proposalPermissions.should(QueryBuilders.matchQuery("collections.owner", user));
        proposalPermissions.must(collectionTypeProposal);

        BoolQueryBuilder subPermissions = QueryBuilders.boolQuery().minimumShouldMatch(1)
                .should(collectionPermissions)
                .should(proposalPermissions);


        BoolQueryBuilder audienceQueryBuilderCollections = QueryBuilders.boolQuery()
                .mustNot(QueryBuilders.termQuery("properties.ccm:restricted_access",true))
                .must(subPermissions);
        audienceQueryBuilder.should(audienceQueryBuilderCollections);

        return audienceQueryBuilder;
    }

    private BoolQueryBuilder getGlobalConditions(List<String> authorityScope, List<String> permissions) {
        BoolQueryBuilder queryBuilderGlobalConditions = (authorityScope != null && authorityScope.size() > 0)
                ? getPermissionsQuery("permissions.read",new HashSet<>(authorityScope))
                : getReadPermissionsQuery();
        queryBuilderGlobalConditions = queryBuilderGlobalConditions.must(QueryBuilders.matchQuery("nodeRef.storeRef.protocol", "workspace"));
        if(permissions != null){
            for(String permission : permissions){
                queryBuilderGlobalConditions = QueryBuilders.boolQuery().must(queryBuilderGlobalConditions).must(getPermissionsQuery("permissions." + permission));
            }
        }

        if(NodeServiceInterceptor.getEduSharingScope() == null){
            queryBuilderGlobalConditions = queryBuilderGlobalConditions.mustNot(QueryBuilders.existsQuery("properties.ccm:eduscopename"));
        }else{
            queryBuilderGlobalConditions = queryBuilderGlobalConditions.must(QueryBuilders.termQuery("properties.ccm:eduscopename.keyword",NodeServiceInterceptor.getEduSharingScope()));
        }
        return queryBuilderGlobalConditions;
    }

    @Override
    public SearchResultNodeRef search(MetadataSet mds, String query, Map<String,String[]> criterias,
                                      SearchToken searchToken) throws Throwable {
        checkClient();
        MetadataQuery queryData;
        try{
            queryData = mds.findQuery(query, MetadataReader.QUERY_SYNTAX_DSL);
        } catch(IllegalArgumentException e){
            logger.info("Query " + query + " is not defined within dsl language, switching to lucene...");
            return super.search(mds,query,criterias,searchToken);
        }

        Set<String> authorities = getUserAuthorities();
        String user = serviceRegistry.getAuthenticationService().getCurrentUserName();


        SearchResultNodeRef sr = new SearchResultNodeRef();
        List<NodeRef> data = new ArrayList<>();
        sr.setData(data);
        try {

            SearchRequest searchRequest = new SearchRequest("workspace");
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.fetchSource(null,searchToken.getExcludes().toArray(new String[]{}));

            QueryBuilder metadataQueryBuilder = MetadataElasticSearchHelper.getElasticSearchQuery(mds.getQueries(MetadataReader.QUERY_SYNTAX_DSL),queryData,criterias);
            BoolQueryBuilder queryBuilder =  QueryBuilders.boolQuery()
                    .must(getGlobalConditions(searchToken.getAuthorityScope(),searchToken.getPermissions()))
                    .must(metadataQueryBuilder);

            if(searchToken.getFacets() != null) {
                for (String facette : searchToken.getFacets()) {
                    searchSourceBuilder.aggregation(AggregationBuilders.terms(facette).field("properties." + facette + ".keyword"));
                }
            }

            /**
             * add collapse builder
             */
            CollapseBuilder collapseBuilder = new CollapseBuilder("properties.ccm:original");
            searchSourceBuilder.collapse(collapseBuilder);
            /**
             * cardinality aggregation to get correct total count
             *
             * https://github.com/elastic/elasticsearch/issues/24130
             */
            searchSourceBuilder.aggregation(AggregationBuilders.cardinality("original_count").field("properties.ccm:original"));



            searchSourceBuilder.query(queryBuilder);
            searchSourceBuilder.from(searchToken.getFrom());
            searchSourceBuilder.size(searchToken.getMaxResult());
            searchSourceBuilder.trackTotalHits(true);
            if(searchToken.getSortDefinition() != null) {
                searchToken.getSortDefinition().applyToSearchSourceBuilder(searchSourceBuilder);
            }




            searchRequest.source(searchSourceBuilder);



            logger.info("query: "+searchSourceBuilder.toString());
            SearchResponse searchResponse = LogTime.log("Searching elastic", () -> client.search(searchRequest, getRequestOptions()));


            SearchHits hits = searchResponse.getHits();
            logger.info("result count: "+hits.getTotalHits());

            long millisPerm = System.currentTimeMillis();
            for (SearchHit hit : hits) {
                data.add(transformSearchHit(authorities, user, hit, searchToken.isResolveCollections()));
            }
            logger.info("permission stuff took:"+(System.currentTimeMillis() - millisPerm));

            Map<String,Map<String,Integer>> facettesResult = new HashMap<String,Map<String,Integer>>();

            Long total = null;
           for(Aggregation a : searchResponse.getAggregations()){
               if(a instanceof  ParsedStringTerms) {
                   ParsedStringTerms pst = (ParsedStringTerms) a;
                   Map<String, Integer> f = new HashMap<>();
                   facettesResult.put(a.getName(), f);
                   for (Terms.Bucket b : pst.getBuckets()) {
                       String key = b.getKeyAsString();
                       long count = b.getDocCount();
                       f.put(key, (int) count);


                   }
               }else if (a instanceof ParsedCardinality){
                   ParsedCardinality pc = (ParsedCardinality)a;
                   if (a.getName().equals("original_count")) {
                       total = pc.getValue();
                   }else{
                       logger.error("unknown cardinality aggregation");
                   }

               }else{
                   logger.error("non supported aggreagtion "+a.getName());
               }
           }
           if(total == null){
               total = hits.getTotalHits().value;
           }
            sr.setCountedProps(facettesResult);
            sr.setStartIDX(searchToken.getFrom());
            sr.setNodeCount((int)total.longValue());
            //client.close();
        } catch (IOException e) {
            logger.error(e.getMessage(),e);
        }


        logger.info("returning");
        return sr;
    }

    private Set<String> getUserAuthorities() {
        Set<String> authorities = serviceRegistry.getAuthorityService().getAuthorities();
        if(!authorities.contains(CCConstants.AUTHORITY_GROUP_EVERYONE))
            authorities.add(CCConstants.AUTHORITY_GROUP_EVERYONE);
        if(!AuthenticationUtil.isRunAsUserTheSystemUser()) {
            authorities.add(AuthenticationUtil.getFullyAuthenticatedUser());
        }
        return authorities;
    }

    public boolean isAllowedToRead(String nodeId){
        boolean result = hasReadPermissionOnNode(nodeId);
        if(result) return true;

        BoolQueryBuilder checkIsChildObjectQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("properties.sys:node-uuid", nodeId))
                .must(QueryBuilders.termQuery("aspects","ccm:io_childobject"));
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(checkIsChildObjectQuery);
        SearchRequest request = new SearchRequest("workspace");
        request.source(searchSourceBuilder);
        try {
            SearchResponse searchResult = client.search(request, getRequestOptions());
            boolean isChildObject = searchResult.getHits().getTotalHits().value > 0;
            if(!isChildObject) return false;

            Map parentRef = (Map) searchResult.getHits().getAt(0).getSourceAsMap().get("parentRef");
            String parentId = (String) parentRef.get("id");
            return hasReadPermissionOnNode(parentId);
        } catch (IOException e) {
            logger.error(e.getMessage(),e);
            return false;
        }
    }
    private boolean hasReadPermissionOnNode(String nodeId){
        try {

            BoolQueryBuilder query = QueryBuilders.boolQuery()
                    .must(getReadPermissionsQuery())
                    .must(QueryBuilders.termQuery("properties.sys:node-uuid", nodeId));
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(query);
            searchSourceBuilder.size(0);
            SearchRequest request = new SearchRequest("workspace");
            request.source(searchSourceBuilder);
            SearchResponse searchResult = client.search(request, getRequestOptions());
            return searchResult.getHits().getTotalHits().value > 0;

        } catch (IOException e) {
            logger.error(e.getMessage(),e);
        }

        return false;
    }

    public NodeRef transformSearchHit(Set<String> authorities, String user, SearchHit hit, boolean resolveCollections) {
        return this.transform(authorities,user,hit.getSourceAsMap(), resolveCollections);
    }
    private NodeRef transform(Set<String> authorities, String user, Map<String, Object> sourceAsMap, boolean resolveCollections){
        Map<String, Serializable> properties = (Map) sourceAsMap.get("properties");

        Map nodeRef = (Map) sourceAsMap.get("nodeRef");
        String nodeId = (String) nodeRef.get("id");

        Map parentRef = (Map) sourceAsMap.get("parentRef");
        String parentId = (parentRef != null) ? (String)parentRef.get("id") :null;


        Map storeRef = (Map) nodeRef.get("storeRef");
        String protocol = (String) storeRef.get("protocol");
        String identifier = (String) storeRef.get("identifier");

        String metadataSet = (String)properties.get(CCConstants.getValidLocalName(CCConstants.CM_PROP_METADATASET_EDU_METADATASET));

        HashMap<String, Object> props = new HashMap<>();

        for (Map.Entry<String, Serializable> entry : properties.entrySet()) {

            Serializable value = null;
            /**
             * @TODO: transform to ValueTool.toMultivalue
             */
            if(entry.getValue() instanceof ArrayList){
                ArrayList<?> list = (ArrayList<?>) entry.getValue();
                if(list.size() > 1 && list.get(0) instanceof String) {
                    value = ValueTool.toMultivalue(list.toArray(new String[0]));
                } else if(list.size() == 1) {
                    value = (Serializable) ((ArrayList<?>) entry.getValue()).get(0);
                }
            } else {
                value = entry.getValue();
            }
            if(entry.getKey().equals("ccm:mediacenter")){
                List<Map<String,Object>> mediacenterStatus = (List<Map<String,Object>>)entry.getValue();
                ArrayList<String> result = new ArrayList<>();
                for(Map<String,Object> mcSt: mediacenterStatus){
                    Gson gson = new Gson();
                    String json = gson.toJson(mcSt);
                    result.add(json);
                }
                value = ValueTool.toMultivalue(result.toArray(new String[result.size()]));
            }
            if(entry.getKey().equals("cm:created") || entry.getKey().equals("cm:modified") && value != null){
                props.put(CCConstants.getValidGlobalName(entry.getKey()) + CCConstants.LONG_DATE_SUFFIX , ((Long)value).toString());
            }
            props.put(CCConstants.getValidGlobalName(entry.getKey()), value);

            /**
             * metadataset translation
             */
            String currentLocale = new AuthenticationToolAPI().getCurrentLocale();
            Map<String,Serializable> i18n = (Map<String,Serializable>)sourceAsMap.get("i18n");
            if(i18n != null){
                Map<String,Serializable> i18nProps = (Map<String,Serializable>)i18n.get(currentLocale);
                if(i18nProps != null){
                   List<String> displayNames = (List<String> )i18nProps.get(entry.getKey());
                    if(displayNames != null){
                        props.put(CCConstants.getValidGlobalName(entry.getKey()) + CCConstants.DISPLAYNAME_SUFFIX, StringUtils.join(displayNames, CCConstants.MULTIVALUE_SEPARATOR));
                    }
                }
            }
        }
        props.put(CCConstants.NODETYPE, sourceAsMap.get("type"));

        List<Map<String, Serializable>> children = (List) sourceAsMap.get("children");
        int childIOCount = 0;
        int usageCount = 0;
        int commentCount = 0;
        if(children != null) {
            for (Map<String, Serializable> child : children) {
                String type = (String)child.get("type");
                List<String> aspects = (List<String>)child.get("aspects");
                if(CCConstants.getValidLocalName(CCConstants.CCM_TYPE_IO).equals(type)
                        && aspects.contains(CCConstants.getValidLocalName(CCConstants.CCM_ASPECT_IO_CHILDOBJECT))){
                    childIOCount++;
                }
                if(CCConstants.getValidLocalName(CCConstants.CCM_TYPE_USAGE).equals(type)){
                    usageCount++;
                }
                if(CCConstants.getValidLocalName(CCConstants.CCM_TYPE_COMMENT).equals(type)){
                    commentCount++;
                }
            }
        }
        if(childIOCount > 0){
            props.put(CCConstants.VIRT_PROP_CHILDOBJECTCOUNT,childIOCount);
        }
        if(usageCount > 0){
            props.put(CCConstants.VIRT_PROP_USAGECOUNT,usageCount);
        }
        if(commentCount > 0){
            props.put(CCConstants.VIRT_PROP_COMMENTCOUNT,commentCount);
        }



        org.alfresco.service.cmr.repository.NodeRef alfNodeRef = new  org.alfresco.service.cmr.repository.NodeRef(new StoreRef(protocol,identifier),nodeId);
        String contentUrl = URLTool.getNgRenderNodeUrl(nodeId,null);
        contentUrl = URLTool.addOAuthAccessToken(contentUrl);
        props.put(CCConstants.CONTENTURL, contentUrl);

        if(sourceAsMap.get("content") != null) {
            props.put(CCConstants.DOWNLOADURL, URLTool.getDownloadServletUrl(alfNodeRef.getId(), null, true));
        }

        if(parentId != null){
            props.put(CCConstants.VIRT_PROP_PRIMARYPARENT_NODEID,parentId);
        }

        NodeRef eduNodeRef = new NodeRefImpl(ApplicationInfoList.getHomeRepository().getAppId(),
                protocol,
                identifier,
                nodeId);
        eduNodeRef.setProperties(props);

        eduNodeRef.setOwner((String)sourceAsMap.get("owner"));

        Map preview = (Map) sourceAsMap.get("preview");
        if(preview != null && preview.get("small") != null) {
            eduNodeRef.setPreview(
                    new NodeRefImpl.PreviewImpl((String) preview.get("mimetype"),
                            Base64.getDecoder().decode((String) preview.get("small")))
            );
        }
        eduNodeRef.setAspects(((List<String>)sourceAsMap.get("aspects")).
                stream().map(CCConstants::getValidGlobalName).filter(Objects::nonNull).collect(Collectors.toList()));

        HashMap<String, Boolean> permissions = new HashMap<>();
        permissions.put(CCConstants.PERMISSION_READ, true);

        long millis = System.currentTimeMillis();

        Map<String,List<String>> permissionsElastic = (Map) sourceAsMap.get("permissions");
        String owner = (String)sourceAsMap.get("owner");
        for(Map.Entry<String,List<String>> entry : permissionsElastic.entrySet()){
            if("read".equals(entry.getKey())){
                continue;
            }

            if(authorities.stream().anyMatch(s -> entry.getValue().contains(s))
                    || entry.getValue().contains(user) ){
                //get fine grained permissions
                PermissionReference pr = permissionModel.getPermissionReference(null,entry.getKey());
                Set<PermissionReference> granteePermissions = permissionModel.getGranteePermissions(pr);
                for(String perm : PermissionServiceHelper.PERMISSIONS){
                    for(PermissionReference pRef : granteePermissions){
                        if(pRef.getName().equals(perm)){
                            permissions.put(perm, true);
                        }
                    }
                }
            }
        }
        if(AuthorityServiceHelper.isAdmin() || user.equals(owner)){
            permissions.put(CCConstants.PERMISSION_CC_PUBLISH,true);
            PermissionReference pr = permissionModel.getPermissionReference(null,"FullControl");
            Set<PermissionReference> granteePermissions = permissionModel.getGranteePermissions(pr);
            for(String perm : PermissionServiceHelper.PERMISSIONS){
                for(PermissionReference pRef : granteePermissions){
                    if(pRef.getName().equals(perm)){
                        permissions.put(perm, true);
                    }
                }
            }

            //Set<PermissionReference> granteePermissions = permissionModel.getGranteePermissions(pr);
            //Set<PermissionReference> immediateGranteePermissions = permissionModel.getImmediateGranteePermissions(pr);

        }


        eduNodeRef.setPermissions(permissions);

        if(resolveCollections) {
            List<Map<String, Object>> collections = (List) sourceAsMap.get("collections");
            if (collections != null) {
                for (Map<String, Object> collection : collections) {
                    String colOwner = (String) collection.get("owner");
                    boolean hasPermission = user.equals(colOwner);
                    if (!hasPermission) {
                        Map<String, List<String>> colPermissionsElastic = (Map) collection.get("permissions");
                        for (Map.Entry<String, List<String>> entry : colPermissionsElastic.entrySet()) {
                            if ("read".equals(entry.getKey())) {
                                hasPermission = entry.getValue().stream().anyMatch(s -> authorities.contains(s) || s.equals(user));
                                break;
                            }
                        }
                    }
                    if (hasPermission) {
                        NodeRef transform = transform(authorities, user, collection, resolveCollections);
                        eduNodeRef.getUsedInCollections().add(transform);
                    }
                }
            }
        }

        long permMillisSingle = (System.currentTimeMillis() - millis);
        return eduNodeRef;
    }

    enum CONTRIBUTOR_PROP {firstname,lastname,email,url,uid};

    @Override
    public Set<SearchVCard> searchContributors(String suggest, List<String> fields, List<String> contributorProperties, ContributorKind contributorKind) throws IOException{
        checkClient();
        SearchRequest searchRequest = new SearchRequest("workspace");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        List<String> searchFields = new ArrayList<>();
        if(fields == null || fields.size() == 0){
            for(CONTRIBUTOR_PROP att : CONTRIBUTOR_PROP.values()){
                searchFields.add("contributor." + att.name());
            }
        }else{
            for(String f : fields){
                if(Stream.of(CONTRIBUTOR_PROP.values()).anyMatch(v -> v.name().equals(f))){
                    searchFields.add("contributor." + f);
                }
            }
        }
        BoolQueryBuilder qb = QueryBuilders.boolQuery();
        for(String searchField : searchFields){
            String search = new String(suggest);
            if(!search.contains("*")) search = "*"+search+"*";
            qb.should(QueryBuilders.wildcardQuery(searchField,search));
        }

        if(contributorProperties.size() > 0) {
            BoolQueryBuilder bqb = QueryBuilders.boolQuery().minimumShouldMatch(1);
            for (String contributorProp : contributorProperties) {
                bqb.should(QueryBuilders.termQuery("contributor.property", contributorProp));
            }
            qb.must(bqb);
        }

        if(contributorKind == ContributorKind.ORGANIZATION){
            qb.must(QueryBuilders.boolQuery().should(
                    QueryBuilders.existsQuery("contributor.X-ROR")
                ).should(
                    QueryBuilders.existsQuery("contributor.X-Wikidata")
                ).minimumShouldMatch(1)
            );
        }else{
            qb.must(QueryBuilders.boolQuery().should(
                    QueryBuilders.existsQuery("contributor.X-ORCID")
                    ).should(
                    QueryBuilders.existsQuery("contributor.X-GND-URI")
                    ).minimumShouldMatch(1)
            );
        }


        searchSourceBuilder.query(qb);
        searchSourceBuilder.from(0);
        searchSourceBuilder.size(0);
        searchSourceBuilder.trackTotalHits(true);
        searchSourceBuilder.sort(new ScoreSortBuilder().order(SortOrder.DESC));
        searchSourceBuilder.aggregation(AggregationBuilders.terms("vcard").field("contributor.vcard.keyword").size(10000));
        searchRequest.source(searchSourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, getRequestOptions());
        ParsedStringTerms aggregation = searchResponse.getAggregations().get("vcard");
        /*
        for (Terms.Bucket bucket : aggregation.getBuckets()) {
            ArrayList<Map<String,Serializable>> contributor = (ArrayList<Map<String,Serializable>>) sourceAsMap.get("contributor");

            List<Map<String,Serializable>> remove = new ArrayList<>();
            for(Map<String,Serializable> map:contributor){
                boolean inResult = false;
                boolean propertyIsInFilter = true;

                if(contributorKind == ContributorKind.ORGANIZATION){
                    if(!map.containsKey("X-ROR") && !map.containsKey("X-Wikidata")){
                        remove.add(map);
                        continue;
                    }
                }else{
                    if(!map.containsKey("X-ORCID") && !map.containsKey("X-GND-URI")){
                        remove.add(map);
                        continue;
                    }
                }

                for(Map.Entry<String,Serializable> entry : map.entrySet()){
                    if(entry.getKey().equals("property")){
                        if(contributorProperties.size() > 0){
                            if(!contributorProperties.contains(entry.getValue())){
                                propertyIsInFilter = false;
                            }
                        }
                        continue;
                    }
                    if(fields.size() > 0 && !fields.contains(entry.getKey())){
                        continue;
                    }
                    if(entry.getValue() == null){
                        continue;
                    }
                    if(((String)entry.getValue()).toLowerCase().contains(suggest.toLowerCase())){
                       inResult = true;
                    }
                }
                if(!inResult || !propertyIsInFilter)remove.add(map);
            }
            for(Map<String,Serializable> map : remove) contributor.remove(map);
            if(contributor.size() > 0) result.addAll(contributor);


        }*/
        VCardEngine engine = new VCardEngine();
        return aggregation.getBuckets().stream().
                map(Terms.Bucket::getKey).
                // this would be nicer via elastic "include" feature, however, it seems to be a pain with the java library
                filter(
                    (k) -> Arrays.stream(
                        suggest.toLowerCase().split(" ")).allMatch(
                                (t) -> k.toString().toLowerCase().contains(t)
                        )
                ).
                filter((k) -> {
                    try {
                        VCard vcard = engine.parse(k.toString());
                        if(contributorKind == ContributorKind.ORGANIZATION){
                            return vcard.getExtendedTypes().stream().map(ExtendedType::getExtendedName).anyMatch(
                                    (e) -> e.equals("X-ROR") || e.equals("X-Wikidata")
                            );
                        } else {
                            return vcard.getExtendedTypes().stream().map(ExtendedType::getExtendedName).anyMatch(
                                    (e) -> e.equals("X-ORCID") || e.equals("X-GND-URI")
                            );
                        }
                    } catch (Exception ignored) {
                        return false;
                    }
                }).
                map((k) -> new SearchVCard(k.toString())).
                collect(Collectors.toCollection(HashSet::new));
    }
    protected RestHighLevelClient getClient() throws IOException {
        checkClient();
        return client;
    }
    public DeleteResponse deleteNative(DeleteRequest deleteRequest) throws IOException {
        return getClient().delete(deleteRequest, getRequestOptions());
    }
    public SearchResponse searchNative(SearchRequest searchRequest) throws IOException {
        return getClient().search(searchRequest, getRequestOptions());
    }
    public UpdateResponse updateNative(UpdateRequest updateRequest) throws IOException {
        return getClient().update(updateRequest, getRequestOptions());
    }
    public SearchResponse scrollNative(SearchScrollRequest searchScrollRequest) throws IOException {
        return getClient().scroll(searchScrollRequest, getRequestOptions());
    }
    public ClearScrollResponse clearScrollNative(ClearScrollRequest clearScrollRequest) throws IOException {
        return getClient().clearScroll(clearScrollRequest, getRequestOptions());
    }
    public void checkClient() throws IOException {
        if(client == null || !client.ping(getRequestOptions())){
             if(client != null){
                 try {
                     client.close();
                 }catch (Exception e){
                     logger.error("ping failed, close failed:" + e.getMessage()+" creating new");
                 }
             }
             client = new RestHighLevelClient(RestClient.builder(getConfiguredHosts()));
        }
    }


}
