package org.edu_sharing.alfrescocontext;

import org.alfresco.repo.i18n.MessageService;
import org.alfresco.repo.lock.JobLockService;
import org.alfresco.repo.nodelocator.NodeLocatorService;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.attributes.AttributeService;
import org.alfresco.service.cmr.coci.CheckOutCheckInService;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.lock.LockService;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.module.ModuleService;
import org.alfresco.service.cmr.rating.RatingService;
import org.alfresco.service.cmr.rendition.RenditionService;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.search.CategoryService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.cmr.tagging.TaggingService;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.alfresco.service.namespace.NamespaceService;
import org.edu_sharing.alfrescocontext.gate.AlfAppContextGate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


/**
 * This will register all Alfresco beans in the edu sharing spring context
 * All Beans are in form of transactional and secure
 */
@Configuration
public class AlfrescoBeanConfig {
    ServiceRegistry serviceRegistry;

    public AlfrescoBeanConfig(){
        ApplicationContext applicationContext = AlfAppContextGate.getApplicationContext();
        serviceRegistry = applicationContext.getBean(ServiceRegistry.SERVICE_REGISTRY, ServiceRegistry.class);
    }

    @Primary
    @Bean(name = "PersonService")
    public PersonService personService(){
        return serviceRegistry.getPersonService();
    }

    @Primary
    @Bean(name = "NodeService")
    public NodeService nodeService(){
        return serviceRegistry.getNodeService();
    }

    @Primary
    @Bean(name = "ActionService")
    public ActionService actionService(){
        return serviceRegistry.getActionService();
    }

    @Primary
    @Bean(name = "AttributeService")
    public AttributeService attributeService(){
        return serviceRegistry.getAttributeService();
    }

    @Primary
    @Bean(name = "AuthenticationService")
    public AuthenticationService authenticationService(){
        return serviceRegistry.getAuthenticationService();
    }

    @Primary
    @Bean(name = "CategoryService")
    public CategoryService categoryService(){
        return serviceRegistry.getCategoryService();
    }

    @Primary
    @Bean(name = "CheckOutCheckInService")
    public CheckOutCheckInService checkOutCheckInService(){
        return serviceRegistry.getCheckOutCheckInService();
    }

    @Primary
    @Bean(name = "ContentService")
    public ContentService contentService(){
        return serviceRegistry.getContentService();
    }

    @Primary
    @Bean(name = "DictionaryService")
    public DictionaryService dictionaryService(){
        return serviceRegistry.getDictionaryService();
    }

    @Primary
    @Bean(name = "FileFolderService")
    public FileFolderService fileFolderService(){
        return serviceRegistry.getFileFolderService();
    }

    @Primary
    @Bean(name = "JobLockService")
    public JobLockService jobLockService(){
        return serviceRegistry.getJobLockService();
    }

    @Primary
    @Bean(name = "CopyService")
    public CopyService copyService(){
        return serviceRegistry.getCopyService();
    }

    @Primary
    @Bean(name = "LockService")
    public LockService lockService(){
        return serviceRegistry.getLockService();
    }

    @Primary
    @Bean(name = "MessageService")
    public MessageService messageService(){
        return serviceRegistry.getMessageService();
    }

    @Primary
    @Bean(name = "MimetypeService")
    public MimetypeService mimetypeService(){
        return serviceRegistry.getMimetypeService();
    }

    @Primary
    @Bean(name = "NamespaceService")
    public NamespaceService namespaceService(){
        return serviceRegistry.getNamespaceService();
    }

    @Primary
    @Bean(name = "NodeLocatorService")
    public NodeLocatorService nodeLocatorService(){
        return serviceRegistry.getNodeLocatorService();
    }

    @Primary
    @Bean(name = "PermissionService")
    public PermissionService permissionService(){
        return serviceRegistry.getPermissionService();
    }

    @Primary
    @Bean(name = "PolicyComponent")
    public PolicyComponent policyComponent(){
        return serviceRegistry.getPolicyComponent();
    }

    @Primary
    @Bean(name = "RatingService")
    public RatingService ratingService(){
        return serviceRegistry.getRatingService();
    }

    @Primary
    @Bean(name = "RenditionService")
    public RenditionService renditionService(){
        return serviceRegistry.getRenditionService();
    }

    @Primary
    @Bean(name = "RetryingTransactionHelper")
    public RetryingTransactionHelper retryingTransactionHelper(){
        return serviceRegistry.getRetryingTransactionHelper();
    }

    @Primary
    @Bean(name = "SearchService")
    public SearchService searchService(){
        return serviceRegistry.getSearchService();
    }

    @Primary
    @Bean(name = "ModuleService")
    public ModuleService moduleService(){
        return serviceRegistry.getModuleService();
    }

    @Primary
    @Bean(name = "SiteService")
    public SiteService siteService(){
        return serviceRegistry.getSiteService();
    }

    @Primary
    @Bean(name = "TaggingService")
    public TaggingService taggingService(){
        return serviceRegistry.getTaggingService();
    }

    @Primary
    @Bean(name = "TemplateService")
    public TemplateService templateService(){
        return serviceRegistry.getTemplateService();
    }

    @Primary
    @Bean(name = "VersionService")
    public VersionService versionService(){
        return serviceRegistry.getVersionService();
    }

    @Primary
    @Bean(name = "WorkflowService")
    public WorkflowService workflowService(){
        return serviceRegistry.getWorkflowService();
    }
}
