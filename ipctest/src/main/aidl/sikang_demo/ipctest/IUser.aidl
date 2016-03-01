// IUser.aidl
package sikang_demo.ipctest;

// Declare any non-default types here with import statements

interface IUser {
   boolean login(String userName,String userPwd);
   void logout(String userName);
}
