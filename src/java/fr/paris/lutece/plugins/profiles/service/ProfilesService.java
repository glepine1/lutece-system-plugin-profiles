/*
 * Copyright (c) 2002-2011, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.profiles.service;

import fr.paris.lutece.plugins.profiles.business.Profile;
import fr.paris.lutece.plugins.profiles.business.ProfileAction;
import fr.paris.lutece.plugins.profiles.business.ProfileFilter;
import fr.paris.lutece.plugins.profiles.business.ProfileHome;
import fr.paris.lutece.plugins.profiles.business.views.View;
import fr.paris.lutece.plugins.profiles.service.action.IProfileActionService;
import fr.paris.lutece.plugins.profiles.utils.constants.ProfilesConstants;
import fr.paris.lutece.portal.business.rbac.AdminRole;
import fr.paris.lutece.portal.business.right.Right;
import fr.paris.lutece.portal.business.user.AdminUser;
import fr.paris.lutece.portal.business.user.AdminUserHome;
import fr.paris.lutece.portal.business.user.attribute.AdminUserField;
import fr.paris.lutece.portal.business.user.attribute.AdminUserFieldHome;
import fr.paris.lutece.portal.business.user.attribute.AttributeHome;
import fr.paris.lutece.portal.business.user.attribute.IAttribute;
import fr.paris.lutece.portal.business.workgroup.AdminWorkgroup;
import fr.paris.lutece.portal.business.workgroup.AdminWorkgroupHome;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.rbac.RBACService;
import fr.paris.lutece.util.ReferenceList;
import fr.paris.lutece.util.html.ItemNavigator;
import fr.paris.lutece.util.url.UrlItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import javax.servlet.http.HttpServletRequest;


/**
 *
 * ProfilesService
 *
 */
public class ProfilesService implements IProfilesService
{
    @Inject
    private IProfileActionService _profileActionService;

    /**
     * {@inheritDoc}
     */
    @Override
    public ItemNavigator getItemNavigator( ProfileFilter pFilter, Profile profile, UrlItem url )
    {
        Plugin plugin = PluginService.getPlugin( ProfilesPlugin.PLUGIN_NAME );
        List<String> listItem = new ArrayList<String>(  );
        Collection<Profile> listAllProfiles = ProfileHome.findProfilesByFilter( pFilter, plugin );
        int nIndex = 0;
        int nCurrentItemId = 0;

        for ( Profile allProfile : listAllProfiles )
        {
            listItem.add( allProfile.getKey(  ) );

            if ( allProfile.getKey(  ).equals( profile.getKey(  ) ) )
            {
                nCurrentItemId = nIndex;
            }

            nIndex++;
        }

        return new ItemNavigator( listItem, nCurrentItemId, url.getUrl(  ), ProfilesConstants.PARAMETER_PROFILE_KEY );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ProfileAction> getListActions( AdminUser user, Profile profile, String strPermission, Locale locale,
        Plugin plugin )
    {
        List<ProfileAction> listActions = new ArrayList<ProfileAction>(  );

        for ( ProfileAction action : _profileActionService.selectActionsList( locale, plugin ) )
        {
            if ( !action.getPermission(  ).equals( strPermission ) )
            {
                listActions.add( action );
            }
        }

        listActions = (List<ProfileAction>) RBACService.getAuthorizedActionsCollection( listActions, profile, user );

        return listActions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doAssignUserToProfile( int nIdUser, HttpServletRequest request, Locale locale )
    {
        AdminUser user = AdminUserHome.findByPrimaryKey( nIdUser );

        if ( user != null )
        {
            ProfilesAdminUserFieldListenerService.getService(  ).doCreateUserFields( user, request, locale );
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doUnassignUserFromProfile( int nIdUser, String strProfileKey, AdminUser currentUser,
        HttpServletRequest request, Locale locale, Plugin plugin )
    {
        AdminUser user = AdminUserHome.findByPrimaryKey( nIdUser );

        // Remove User Fields
        List<IAttribute> listAttributes = AttributeHome.findPluginAttributes( ProfilesPlugin.PLUGIN_NAME, locale );
        IAttribute attribute = listAttributes.get( 0 );
        String strValue = request.getParameter( ProfilesConstants.PARAMETER_ATTRIBUTE + ProfilesConstants.UNDERSCORE +
                attribute.getIdAttribute(  ) );
        int nIdField = Integer.parseInt( strValue );
        List<AdminUserField> listUserFields = AdminUserFieldHome.selectUserFieldsByIdUserIdAttribute( user.getUserId(  ),
                attribute.getIdAttribute(  ) );

        for ( AdminUserField userField : listUserFields )
        {
            if ( userField.getAttributeField(  ).getIdField(  ) == nIdField )
            {
                AdminUserFieldHome.remove( userField );

                break;
            }
        }

        // Remove profile
        removeUserFromProfile( strProfileKey, nIdUser, plugin );

        // Remove rights to the user
        for ( Right right : getRightsListForProfile( strProfileKey, plugin ) )
        {
            if ( AdminUserHome.hasRight( user, right.getId(  ) ) &&
                    ( ( user.getUserLevel(  ) > currentUser.getUserLevel(  ) ) || currentUser.isAdmin(  ) ) )
            {
                AdminUserHome.removeRightForUser( nIdUser, right.getId(  ) );
            }
        }

        // Remove roles to the user
        for ( AdminRole role : getRolesListForProfile( strProfileKey, plugin ) )
        {
            if ( AdminUserHome.hasRole( user, role.getKey(  ) ) )
            {
                AdminUserHome.removeRoleForUser( nIdUser, role.getKey(  ) );
            }
        }

        // Remove workgroups to the user
        for ( AdminWorkgroup workgroup : getWorkgroupsListForProfile( strProfileKey, plugin ) )
        {
            if ( AdminWorkgroupHome.isUserInWorkgroup( user, workgroup.getKey(  ) ) )
            {
                AdminWorkgroupHome.removeUserFromWorkgroup( user, workgroup.getKey(  ) );
            }
        }
    }

    /**
    * Creation of an instance of profile
    * @param profile The instance of the profile which contains the informations to store
    * @param plugin Plugin
    * @return The instance of profile which has been created with its primary key.
    */
    @Override
    public Profile create( Profile profile, Plugin plugin )
    {
        if ( profile != null )
        {
            ProfileHome.create( profile, plugin );
        }

        return profile;
    }

    /**
     * Update of the profile which is specified in parameter
     * @param profile The instance of the profile which contains the new data to store
     * @param plugin Plugin
     * @return The instance of the profile which has been updated
     */
    @Override
    public Profile update( Profile profile, Plugin plugin )
    {
        if ( profile != null )
        {
            ProfileHome.update( profile, plugin );
        }

        return profile;
    }

    /**
     * Remove the Profile whose identifier is specified in parameter
     * @param strProfileKey The Profile object to remove
     * @param plugin Plugin
     */
    @Override
    public void remove( String strProfileKey, Plugin plugin )
    {
        ProfileHome.remove( strProfileKey, plugin );
    }

    ///////////////////////////////////////////////////////////////////////////
    // Finders

    /**
     * Returns an instance of a profile whose identifier is specified in parameter
     * @param strProfileKey The key of the profile
     * @param plugin Plugin
     * @return An instance of profile
     */
    @Override
    public Profile findByPrimaryKey( String strProfileKey, Plugin plugin )
    {
        return ProfileHome.findByPrimaryKey( strProfileKey, plugin );
    }

    /**
     * Returns a collection of profiles objects
     * @param plugin Plugin
     * @return A collection of profiles
     */
    @Override
    public List<Profile> findAll( Plugin plugin )
    {
        return ProfileHome.findAll( plugin );
    }

    /**
     * Find profile by filter
     * @param pFilter the Filter
     * @param plugin Plugin
     * @return List of profiles
     */
    @Override
    public List<Profile> findProfilesByFilter( ProfileFilter pFilter, Plugin plugin )
    {
        return ProfileHome.findProfilesByFilter( pFilter, plugin );
    }

    /**
     * Check if a profile already exists or not
     * @param strProfileKey The profile key
     * @param plugin Plugin
     * @return true if it already exists
     */
    @Override
    public boolean checkExistProfile( String strProfileKey, Plugin plugin )
    {
        return ProfileHome.checkExistProfile( strProfileKey, plugin );
    }

    /**
     * Get the list of profiles
     * @param plugin Plugin
     * @return the list of profiles
     */
    @Override
    public ReferenceList getProfilesList( Plugin plugin )
    {
        return ProfileHome.getProfilesList( plugin );
    }

    /**
     * Check if the profile is attributed to any user
     * @param strProfileKey the profile key
     * @param plugin Plugin
     * @return true if it is attributed to at least one user, false otherwise
     */
    @Override
    public boolean checkProfileAttributed( String strProfileKey, Plugin plugin )
    {
        return ProfileHome.checkProfileAttributed( strProfileKey, plugin );
    }

    /**
     * Load the profile by a given ID user
     * @param nIdUser the ID user
     * @param plugin Plugin
     * @return a profile
     */
    @Override
    public Profile findProfileByIdUser( int nIdUser, Plugin plugin )
    {
        return ProfileHome.findProfileByIdUser( nIdUser, plugin );
    }

    /* RIGHTS */

    /**
     * Get the list of rights associated to the profile
     * @param strProfileKey The profile Key
     * @param plugin Plugin
     * @return The list of Right
     */
    @Override
    public List<Right> getRightsListForProfile( String strProfileKey, Plugin plugin )
    {
        return ProfileHome.getRightsListForProfile( strProfileKey, plugin );
    }

    /**
     * Check if a profile has the given right.
     * @param strProfileKey The profile Key
     * @param strIdRight The Right ID
     * @param plugin Plugin
     * @return true if the profile has the right, false otherwise
     */
    @Override
    public boolean hasRight( String strProfileKey, String strIdRight, Plugin plugin )
    {
        return ProfileHome.hasRight( strProfileKey, strIdRight, plugin );
    }

    /**
     * Add a right for a profile
     * @param strProfileKey The profile Key
     * @param strIdRight The Right ID
     * @param plugin Plugin
     */
    @Override
    public void addRightForProfile( String strProfileKey, String strIdRight, Plugin plugin )
    {
        ProfileHome.addRightForProfile( strProfileKey, strIdRight, plugin );
    }

    /**
     * Remove a right from a profile
     * @param strProfileKey The profile Key
     * @param strIdRight The Right ID
     * @param plugin Plugin
     */
    @Override
    public void removeRightFromProfile( String strProfileKey, String strIdRight, Plugin plugin )
    {
        ProfileHome.removeRightFromProfile( strProfileKey, strIdRight, plugin );
    }

    /**
     * Remove all rights from profile
     * @param strProfileKey The profile key
     * @param plugin Plugin
     */
    @Override
    public void removeRights( String strProfileKey, Plugin plugin )
    {
        ProfileHome.removeRights( strProfileKey, plugin );
    }

    /* WORKGROUPS */

    /**
     * Get the list of workgroups associated to the profile
     * @param strProfileKey The profile Key
     * @param plugin Plugin
     * @return The list of workgroups
     */
    @Override
    public List<AdminWorkgroup> getWorkgroupsListForProfile( String strProfileKey, Plugin plugin )
    {
        return ProfileHome.getWorkgroupsListForProfile( strProfileKey, plugin );
    }

    /**
     * Check if a profile has the given workgroup.
     * @param strProfileKey The profile Key
     * @param strWorkgroupKey The Workgroup key
     * @param plugin Plugin
     * @return true if the profile has the workgroup, false otherwise
     */
    @Override
    public boolean hasWorkgroup( String strProfileKey, String strWorkgroupKey, Plugin plugin )
    {
        return ProfileHome.hasWorkgroup( strProfileKey, strWorkgroupKey, plugin );
    }

    /**
     * Add a workgroup for a profile
     * @param strProfileKey The profile Key
     * @param strWorkgroupKey The WorkgroupKey
     * @param plugin Plugin
     */
    @Override
    public void addWorkgroupForProfile( String strProfileKey, String strWorkgroupKey, Plugin plugin )
    {
        ProfileHome.addWorkgroupForProfile( strProfileKey, strWorkgroupKey, plugin );
    }

    /**
     * Remove a workgroup from a profile
     * @param strProfileKey The profile Key
     * @param strWorkgroupKey The Workgroup key
     * @param plugin Plugin
     */
    @Override
    public void removeWorkgroupFromProfile( String strProfileKey, String strWorkgroupKey, Plugin plugin )
    {
        ProfileHome.removeWorkgroupFromProfile( strProfileKey, strWorkgroupKey, plugin );
    }

    /**
     * Remove all workgroups from profile
     * @param strProfileKey The profile key
     * @param plugin Plugin
     */
    @Override
    public void removeWorkgroups( String strProfileKey, Plugin plugin )
    {
        ProfileHome.removeWorkgroups( strProfileKey, plugin );
    }

    /* ROLES */

    /**
     * Get the list of roles associated to the profile
     * @param strProfileKey The profile Key
     * @param plugin Plugin
     * @return The list of roles
     */
    @Override
    public List<AdminRole> getRolesListForProfile( String strProfileKey, Plugin plugin )
    {
        return ProfileHome.getRolesListForProfile( strProfileKey, plugin );
    }

    /**
     * Check if a profile has the given role.
     * @param strProfileKey The profile Key
     * @param strRoleKey The Role key
     * @param plugin Plugin
     * @return true if the profile has the role, false otherwise
     */
    @Override
    public boolean hasRole( String strProfileKey, String strRoleKey, Plugin plugin )
    {
        return ProfileHome.hasRole( strProfileKey, strRoleKey, plugin );
    }

    /**
     * Add a role for a profile
     * @param strProfileKey The profile Key
     * @param strRoleKey The RoleKey
     * @param plugin Plugin
     */
    @Override
    public void addRoleForProfile( String strProfileKey, String strRoleKey, Plugin plugin )
    {
        ProfileHome.addRoleForProfile( strProfileKey, strRoleKey, plugin );
    }

    /**
     * Remove a role from a profile
     * @param strProfileKey The profile Key
     * @param strRoleKey The role key
     * @param plugin Plugin
     */
    @Override
    public void removeRoleFromProfile( String strProfileKey, String strRoleKey, Plugin plugin )
    {
        ProfileHome.removeRoleFromProfile( strProfileKey, strRoleKey, plugin );
    }

    /**
     * Remove all roles from profile
     * @param strProfileKey The profile key
     * @param plugin Plugin
     */
    @Override
    public void removeRoles( String strProfileKey, Plugin plugin )
    {
        ProfileHome.removeRoles( strProfileKey, plugin );
    }

    /* USERS */

    /**
     * Get the list of users associated to the profile
     * @param strProfileKey The profile Key
     * @param plugin Plugin
     * @return The list of users
     */
    @Override
    public List<AdminUser> getUsersListForProfile( String strProfileKey, Plugin plugin )
    {
        return ProfileHome.getUsersListForProfile( strProfileKey, plugin );
    }

    /**
     * Check if a profile has the given user.
     * @param strProfileKey The profile Key
     * @param nIdUser The User ID
     * @param plugin Plugin
     * @return true if the profile has the user, false otherwise
     */
    @Override
    public boolean hasUser( String strProfileKey, int nIdUser, Plugin plugin )
    {
        return ProfileHome.hasUser( strProfileKey, nIdUser, plugin );
    }

    /**
     * Add an user for a profile
     * @param strProfileKey The profile Key
     * @param nIdUser The User ID
     * @param plugin Plugin
     */
    @Override
    public void addUserForProfile( String strProfileKey, int nIdUser, Plugin plugin )
    {
        ProfileHome.addUserForProfile( strProfileKey, nIdUser, plugin );
    }

    /**
     * Remove a user from a profile
     * @param strProfileKey The profile Key
     * @param nIdUser The User ID
     * @param plugin Plugin
     */
    @Override
    public void removeUserFromProfile( String strProfileKey, int nIdUser, Plugin plugin )
    {
        ProfileHome.removeUserFromProfile( strProfileKey, nIdUser, plugin );
    }

    /**
     * Remove all users from profile
     * @param strProfileKey The profile key
     * @param plugin Plugin
     */
    @Override
    public void removeUsers( String strProfileKey, Plugin plugin )
    {
        ProfileHome.removeUsers( strProfileKey, plugin );
    }

    /**
     * Remove all profiles associated to an user
     * @param nIdUser The User ID
     * @param plugin Plugin
     */
    @Override
    public void removeProfilesFromUser( int nIdUser, Plugin plugin )
    {
        ProfileHome.removeProfilesFromUser( nIdUser, plugin );
    }

    /**
     * Check if the given user has a profile or not
     * @param nIdUser the ID user
     * @param plugin Plugin
     * @return true if the user has the profile, false otherwise
     */
    @Override
    public boolean hasProfile( int nIdUser, Plugin plugin )
    {
        return ProfileHome.hasProfile( nIdUser, plugin );
    }

    /* VIEW */

    /**
     * Get the view associated to the profile
     * @param strProfileKey the profile key
     * @param plugin Plugin
     * @return the view
     */
    @Override
    public View getViewForProfile( String strProfileKey, Plugin plugin )
    {
        return ProfileHome.getViewForProfile( strProfileKey, plugin );
    }

    /**
     * Remove profile from a view
     * @param strProfileKey the profile key
     * @param plugin Plugin
     */
    @Override
    public void removeView( String strProfileKey, Plugin plugin )
    {
        ProfileHome.removeView( strProfileKey, plugin );
    }
}
