/*******************************************************************************
 * QBiC User DB Tools enables users to add people and affiliations to our mysql user database.
 * Copyright (C) 2016 Andreas Friedrich
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package life.qbic.main;

        import java.io.File;
        import java.io.IOException;
        import java.util.ArrayList;
        import java.util.Arrays;
        import java.util.HashMap;
        import java.util.List;
        import java.util.Map;
        import java.util.Set;

        import javax.servlet.annotation.WebServlet;
        import com.vaadin.annotations.Widgetset;

        //import ldap.LDAPConfig;
        import life.qbic.openbis.openbisclient.IOpenBisClient;
        import life.qbic.openbis.openbisclient.OpenBisClient;
        import life.qbic.openbis.openbisclient.OpenBisClientMock;
        import life.qbic.portal.liferayandvaadinhelpers.main.LiferayAndVaadinUtils;
        import life.qbic.logging.Log4j2Logger;
        import life.qbic.model.Affiliation;
        import life.qbic.model.CollaboratorWithResponsibility;
        import life.qbic.model.Person;
        import life.qbic.model.ProjectInfo;
        import life.qbic.model.Styles;
        import life.qbic.model.Styles.NotificationType;
        import life.qbic.model.Tuple;
        import life.qbic.views.AffiliationInput;
        import life.qbic.views.AffiliationVIPTab;
        import life.qbic.views.MultiAffiliationTab;
        import life.qbic.views.PersonInput;
        import life.qbic.views.ProjectView;
        import life.qbic.views.SearchView;

        import com.liferay.portal.model.User;
        import com.liferay.portal.model.UserGroup;
        import com.vaadin.annotations.Theme;
        import com.vaadin.annotations.VaadinServletConfiguration;
        import com.vaadin.data.Property.ValueChangeEvent;
        import com.vaadin.data.Property.ValueChangeListener;
        import com.vaadin.server.VaadinRequest;
        import com.vaadin.server.VaadinServlet;
        import com.vaadin.shared.ui.label.ContentMode;
        import com.vaadin.ui.Button;
        import com.vaadin.ui.Label;
        import com.vaadin.ui.TabSheet;
        import com.vaadin.ui.UI;
        import com.vaadin.ui.VerticalLayout;
        import com.vaadin.ui.Button.ClickEvent;
        import com.vaadin.ui.Button.ClickListener;

        import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Experiment;
        import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Project;
        import life.qbic.config.ConfigurationManagerFactory;
        import life.qbic.db.Config;
        import life.qbic.db.DBManager;

@Theme("mytheme")
@SuppressWarnings("serial")
@Widgetset("life.qbic.AppWidgetSet")
public class MyPortletUI extends UI {

//    @WebServlet(value = "/*", asyncSupported = true)
//    @VaadinServletConfiguration(productionMode = false, ui = MyPortletUI.class,
//            widgetset = "qdbtools.main.widgetset.QuserdbtoolsWidgetset")
    public static class Servlet extends VaadinServlet {
    }

    private life.qbic.logging.Logger logger = new Log4j2Logger(MyPortletUI.class);
    private DBManager dbControl;
    private Map<String, Integer> affiMap;
    private Map<String, Integer> personMap;

    private TabSheet options;

    private Config config;
    public static String tmpFolder;

    private IOpenBisClient openbis;
    private boolean testMode = false;

    @Override
    protected void init(VaadinRequest request) {
        final VerticalLayout layout = new VerticalLayout();
        layout.setMargin(true);
        setContent(layout);

        options = new TabSheet();

        this.config = readConfig();
        tmpFolder = config.getTmpFolder();
        //LDAPConfig ldapConfig = readLdapConfig();// TODO

        // establish connection to the OpenBIS API
        if (!isDevelopment() || !testMode) {
            try {
                this.openbis = new OpenBisClient(config.getOpenbisUser().trim(), config.getOpenbisPass().trim(),
                        config.getOpenbisURL().trim());
                this.openbis.login();
            } catch (Exception e) {
                // success = false;
                // logger.error(
                // "User \"" + userID + "\" could not connect to openBIS and has been informed of this.");
                // layout.addComponent(new Label(
                // "Data Management System could not be reached. Please try again later or contact us."));
            }
        }
        if (isDevelopment() && testMode) {
            logger.error("No connection to openBIS. Trying mock version for testing.");
            this.openbis = new OpenBisClientMock("", "", "");
            layout.addComponent(new Label(
                    "openBIS could not be reached. Resuming with mock version. Some options might be non-functional. Reload to retry."));
        }

        dbControl = new DBManager(config);

        initTabs();

        layout.addComponent(options);
    }

    private void initTabs() {
        boolean admin = isAdmin();
        options.removeAllComponents();
        if (!admin && !isDevelopment() && !canUsePortlet()) {
            VerticalLayout rightsMissingTab = new VerticalLayout();
            rightsMissingTab.setCaption("User Database Input");
            Label info = new Label(
                    "Your account does not have the necessary rights to add new people to our database.\n"
                            + "If you think you should be able to do so, please contact us.",
                    ContentMode.PREFORMATTED);
            rightsMissingTab.addComponent(info);
            options.addTab(rightsMissingTab, "Information");
            options.setSelectedTab(rightsMissingTab);
            options.setEnabled(false);

        } else {

            affiMap = dbControl.getAffiliationMap();
            personMap = dbControl.getPersonMap();
            Set<String> instituteNames = dbControl.getInstituteNames();
            List<String> facultyEnums =
                    dbControl.getPossibleEnumsForColumnsInTable("organizations", "faculty");
            List<String> affiliationRoles =
                    dbControl.getPossibleEnumsForColumnsInTable("persons_organizations", "occupation");

            PersonInput addUserTab =
                    new PersonInput(dbControl.getPossibleEnumsForColumnsInTable("persons", "title"), affiMap,
                            affiliationRoles, new AffiliationInput(instituteNames, facultyEnums, personMap));
            options.addTab(addUserTab, "New Person");

            AffiliationInput addAffilTab = new AffiliationInput(instituteNames, facultyEnums, personMap);
            options.addTab(addAffilTab, "New Affiliation");


            SearchView searchView = new SearchView();
            options.addTab(searchView, "Search Entries");

            List<Affiliation> affiTable = dbControl.getAffiliationTable();
            Map<Integer, Tuple> affiPeople = new HashMap<Integer, Tuple>();
            for (Affiliation a : affiTable) {
                int id = a.getID();
                affiPeople.put(id, new Tuple(a.getContactPerson(), a.getHeadName()));
            }

            AffiliationVIPTab vipTab = new AffiliationVIPTab(personMap, affiMap, affiPeople);
            options.addTab(vipTab, "Edit Affiliation VIPs");

            MultiAffiliationTab multiAffilTab =
                    new MultiAffiliationTab(personMap, affiMap, affiliationRoles);
            options.addTab(multiAffilTab, "Additional Person-Affiliations");
            if (!admin) {
                options.getTab(3).setEnabled(false);
                options.getTab(4).setEnabled(false);
            }

            String userID = "";
            if (LiferayAndVaadinUtils.isLiferayPortlet()) {
                logger.info("DB Tools running on Liferay, fetching user ID.");
                userID = LiferayAndVaadinUtils.getUser().getScreenName();
            } else {
                if (isDevelopment()) {
                    logger.warn("Checks for local dev version successful. User is granted admin status.");
                    userID = "admin";
                }
            }
            Map<String, ProjectInfo> userProjects = new HashMap<String, ProjectInfo>();

            List<Project> openbisProjects = new ArrayList<Project>();

            if (testMode) {

                openbisProjects = openbis.listProjects();
            } else {

                openbisProjects = openbis.getOpenbisInfoService()
                        .listProjectsOnBehalfOfUser(openbis.getSessionToken(), userID);
            }
            Map<String, ProjectInfo> allProjects = dbControl.getProjectMap();
            for (Project p : openbisProjects) {
                String projectID = p.getIdentifier();
                String code = p.getCode();
                if (allProjects.get(projectID) == null)
                    userProjects.put(projectID, new ProjectInfo(p.getSpaceCode(), code, "", -1));
                else
                    userProjects.put(projectID, allProjects.get(projectID));
            }

            ProjectView projectView = new ProjectView(userProjects.values(), openbis, personMap);
            options.addTab(projectView, "Projects");
            options.getTab(5).setEnabled(!userProjects.isEmpty());

            initPortletToDBFunctionality(addAffilTab, addUserTab, multiAffilTab, vipTab, searchView,
                    projectView);
        }
    }

    boolean isDevelopment() {
        return !LiferayAndVaadinUtils.isLiferayPortlet();
    }

    private boolean canUsePortlet() {
        try {
            User user = LiferayAndVaadinUtils.getUser();
            for (UserGroup grp : user.getUserGroups()) {
                String group = grp.getName();
                if (config.getUserGrps().contains(group)) {
                    logger.info("User " + user.getScreenName() + " can use portlet because they are part of "
                            + group);
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Could not fetch user groups. User won't be able to use portlet.");
        }
        return false;
    }

    private boolean isAdmin() {
        if (isDevelopment())
            return true;
        else {
            try {
                User user = LiferayAndVaadinUtils.getUser();
                if(user == null){
                    System.out.println("user null");
                }
                for (UserGroup grp : user.getUserGroups()) {
                    String group = grp.getName();
                    if (config.getAdminGrps().contains(group)) {
                        logger.info("User " + user.getScreenName()
                                + " has full rights because they are part of " + group);
                        return true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Could not fetch user groups. User won't be able to use portlet.");
            }
            return false;
        }
    }

    private void initPortletToDBFunctionality(final AffiliationInput addAffilTab,
                                              final PersonInput addUserTab, final MultiAffiliationTab multiAffilTab,
                                              final AffiliationVIPTab vipTab, final SearchView search, final ProjectView projects) {

        projects.getProjectTable().addValueChangeListener(new ValueChangeListener() {

            private Map<String, String> expTypeCodeTranslation = new HashMap<String, String>() {
                {
                    put("Q_EXPERIMENTAL_DESIGN", "Patients/Sources");
                    put("Q_SAMPLE_EXTRACTION", "Sample Extracts");
                    put("Q_SAMPLE_PREPARATION", "Sample Preparations");
                    put("Q_MS_MEASUREMENT", "Mass Spectrometry");
                    put("Q_NGS_MEASUREMENT", "NGS Sequencing");
                };
            };

            @Override
            public void valueChange(ValueChangeEvent event) {
                Object item = projects.getProjectTable().getValue();
                if (item != null) {
                    String project = item.toString();
                    // get collaborators associated to openbis experiments
                    List<CollaboratorWithResponsibility> collaborators =
                            dbControl.getCollaboratorsOfProject(project);
                    // get openbis experiments and type
                    Map<String, String> existingExps = new HashMap<String, String>();
                    for (Experiment e : openbis.getExperimentsForProject2(project)) {
                        String type = expTypeCodeTranslation.get(e.getExperimentTypeCode());
                        String id = e.getIdentifier();
                        if (type != null)
                            existingExps.put(id, type);
                    }
                    // add types for experiments with existing collaborators
                    for (CollaboratorWithResponsibility c : collaborators) {
                        String identifier = c.getOpenbisIdentifier();
                        c.setType(existingExps.get(identifier));
                        existingExps.remove(identifier);
                    }
                    // add empty entries and type for applicable experiments without collaborators
                    for (String expID : existingExps.keySet()) {
                        String code = expID.split("/")[3];
                        CollaboratorWithResponsibility c =
                                new CollaboratorWithResponsibility(-1, "", expID, code, "Contact");
                        c.setType(existingExps.get(expID));
                        collaborators.add(c);
                    }
                    projects.setCollaboratorsOfProject(collaborators);
                }
            }
        });

        projects.getInfoCommitButton().addClickListener(new ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                ProjectInfo info = projects.getEditedInfo();
                if (info != null) {
                    String code = info.getProjectCode();
                    int id = info.getProjectID();
                    if (id < 1)
                        id = dbControl.addProjectToDB("/" + info.getSpace() + "/" + code,
                                info.getProjectName());
                    else
                        dbControl.addOrChangeSecondaryNameForProject(id, info.getProjectName());
                    if (info.getInvestigator() == null || info.getInvestigator().isEmpty())
                        dbControl.removePersonFromProject(id, "PI");
                    else
                        dbControl.addOrUpdatePersonToProject(id, personMap.get(info.getInvestigator()), "PI");
                    if (info.getContact() == null || info.getContact().isEmpty())
                        dbControl.removePersonFromProject(id, "Contact");
                    else
                        dbControl.addOrUpdatePersonToProject(id, personMap.get(info.getContact()), "Contact");
                    if (info.getManager() == null || info.getManager().isEmpty())
                        dbControl.removePersonFromProject(id, "Manager");
                    else
                        dbControl.addOrUpdatePersonToProject(id, personMap.get(info.getManager()), "Manager");
                    projects.updateChangedInfo(info);
                }
            }
        });;
        projects.getPeopleCommitButton().addClickListener(new ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                List<CollaboratorWithResponsibility> links = projects.getNewResponsibilities();
                for (CollaboratorWithResponsibility c : links) {
                    int experimentID = c.getExperimentID();
                    if (experimentID < 1)
                        experimentID = dbControl.addExperimentToDB(c.getOpenbisIdentifier());
                    String name = c.getPerson();
                    int personID = -1;
                    if (personMap.get(name) != null)
                        personID = personMap.get(name);
                    if (personID < 1)
                        dbControl.removePersonFromExperiment(experimentID);
                    else
                        dbControl.addOrUpdatePersonToExperiment(experimentID, personID, "Contact");
                }
            }
        });;

        search.getSearchAffiliationButton().addClickListener(new Button.ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                String affi = search.getAffiliationSearchField().getValue();
                if (affi != null && !affi.isEmpty()) {
                    search.setAffiliations(dbControl.getAffiliationsContaining(affi));
                } else
                    search.setAffiliations(new ArrayList<Affiliation>());
            }
        });

        search.getSearchPersonButton().addClickListener(new Button.ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                String person = search.getPersonSearchField().getValue();
                if (person != null && !person.isEmpty()) {
                    search.setPersons(dbControl.getPersonsContaining(person));
                } else
                    search.setPersons(new ArrayList<Person>());
            }
        });

        addAffilTab.getCommitButton().addClickListener(new Button.ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                if (addAffilTab.isValid()) {
                    if (dbControl.addNewAffiliation(addAffilTab.getAffiliation()) > -1)
                        successfulCommit();
                    else
                        commitError("There has been an error.");
                } else
                    inputError();
            }
        });

        vipTab.getSetHeadAndContactButton().addClickListener(new Button.ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                int affi = vipTab.getSelectedAffiTabID();
                int contact = vipTab.getNewContactID();
                int head = vipTab.getNewHeadID();
                if (affi > 0) {
                    if (head > 0)
                        dbControl.setAffiliationVIP(affi, head, "head");
                    if (contact > 0)
                        dbControl.setAffiliationVIP(affi, contact, "main_contact");
                    vipTab.updateVIPs();
                    successfulCommit();
                }
            }
        });

        addAffilTab.getInstituteField().addValueChangeListener(new ValueChangeListener() {

            @Override
            public void valueChange(ValueChangeEvent event) {
                Object val = addAffilTab.getInstituteField().getValue();
                if (val != null) {
                    Affiliation orgInfo = dbControl.getOrganizationInfosFromInstitute(val.toString());
                    if (orgInfo != null)
                        addAffilTab.autoComplete(orgInfo);
                }
            }
        });

        addUserTab.getCommitButton().addClickListener(new Button.ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                if (addUserTab.isValid()) {
                    Person p = addUserTab.getPerson();
                    if (addUserTab.hasNewAffiliation()) {
                        int affiID = dbControl.addNewAffiliation(addUserTab.getNewAffiliation());
                        if (affiID > -1)
                            successfulCommit();
                        else
                            commitError("There has been an error while adding the new affiliation.");
                        p.setAffiliationID(affiID);
                    }
                    if (dbControl.userNameExists(p.getUsername())) {
                        Styles.notification("Person already registered",
                                "A person with the Username you selected is already registered in our database!",
                                NotificationType.ERROR);
                    } else {
                        if (dbControl.addNewPerson(p))
                            successfulCommit();
                        else
                            commitError("There has been an error while adding a new person.");
                    }
                } else
                    inputError();
            }
        });

        multiAffilTab.getCommitButton().addClickListener(new Button.ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                if (multiAffilTab.isValid()) {
                    if (dbControl.addOrUpdatePersonAffiliationConnections(
                            personMap.get(multiAffilTab.getPersonBox().getValue()),
                            multiAffilTab.getChangedAndNewConnections()))
                        successfulCommit();
                    else
                        commitError("There has been an error.");
                } else
                    inputError();
            }
        });

        multiAffilTab.getAddButton().addClickListener(new Button.ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                String personName = multiAffilTab.getPersonBox().getValue().toString();
                Person p = dbControl.getPerson(personMap.get(personName));

                String affiName = multiAffilTab.getOrganizationBox().getValue().toString();
                Person newP = new Person(p.getUsername(), p.getTitle(), p.getFirst(), p.getLast(),
                        p.geteMail(), p.getPhone(), affiMap.get(affiName), affiName, "");
                multiAffilTab.addDataToTable(new ArrayList<Person>(Arrays.asList(newP)));
                multiAffilTab.getAddButton().setEnabled(false);
            }
        });

        ValueChangeListener multiAffiPersonListener = new ValueChangeListener() {

            @Override
            public void valueChange(ValueChangeEvent event) {
                if (multiAffilTab.getPersonBox().getValue() != null) {
                    String personName = multiAffilTab.getPersonBox().getValue().toString();
                    multiAffilTab.reactToPersonSelection(
                            dbControl.getPersonWithAffiliations(personMap.get(personName)));
                    multiAffilTab.getAddButton().setEnabled(multiAffilTab.newAffiliationPossible());
                }
            }
        };
        multiAffilTab.getPersonBox().addValueChangeListener(multiAffiPersonListener);

        ValueChangeListener multiAffiListener = new ValueChangeListener() {

            @Override
            public void valueChange(ValueChangeEvent event) {
                if (multiAffilTab.getPersonBox().getValue() != null) {
                    multiAffilTab.getAddButton().setEnabled(multiAffilTab.newAffiliationPossible());
                }
            }
        };
        multiAffilTab.getOrganizationBox().addValueChangeListener(multiAffiListener);
    }

    private void successfulCommit() {
        Styles.notification("Data added", "Data has been successfully added to the database!",
                NotificationType.SUCCESS);
        // wait a bit and reload tabs
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        initTabs();
    }

    private void inputError() {
        Styles.notification("Data Incomplete", "Please fill in all required fields correctly.",
                NotificationType.DEFAULT);
    }

    private void commitError(String reason) {
        Styles.notification("There has been an error.", reason, NotificationType.ERROR);
    }

    private Config readConfig() {
        life.qbic.config.ConfigurationManager c = ConfigurationManagerFactory.getInstance();

        return new Config(c.getMysqlHost(), c.getMysqlPort(), c.getMysqlDB(), c.getMysqlUser(),
                c.getMysqlPass(), c.getDBInputUserGrps(), c.getDBInputAdminGrps(), c.getDataSourceUrl(),
                c.getDataSourceUser(), c.getDataSourcePassword(), c.getTmpFolder());
    }

//    private LDAPConfig readLdapConfig() {
//        config.ConfigurationManager c = ConfigurationManagerFactory.getInstance();
//
//        return new LDAPConfig(c.getLdapHost(), c.getLdapBase(), c.getLdapUser(), c.getLdapPass());
//    }
}