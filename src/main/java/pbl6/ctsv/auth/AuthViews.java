package pbl6.ctsv.auth;

import java.time.Instant;
import java.util.*;

final class AuthViews {
    private AuthViews(){}
    record Role(String id,String code,String name,boolean active){}
    record Function(String id,String code,String name,boolean active){}
    record ProfileLink(String sourceSystem,String profileType,String sourceId){}
    record AccountSelf(UUID accountId,String username,boolean active,List<Role> roles,List<Function> functions){}
    record AccountAdmin(UUID accountId,String username,boolean active,List<Role> roles,List<Function> functions,List<ProfileLink> profileLinks){}
    record AccountResult(AccountAdmin account,String etag){}
    record TokenResponse(String accessToken,String tokenType,int expiresIn,Instant refreshExpiresAt,UUID accountId){
        @Override public String toString(){return "TokenResponse[accountId="+accountId+"]";}
    }
    record TokenIssue(TokenResponse response,String cookie){
        @Override public String toString(){return "TokenIssue[accountId="+response.accountId()+"]";}
    }
    record RoleFunction(String roleId,String functionId){}
    record AccountRole(UUID accountId,String roleId){}
    record RbacView(List<Role> roles,List<Function> functions,List<RoleFunction> roleFunctions,List<AccountRole> accountRoles){}
}
