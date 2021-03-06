package com.pranav.java.ide.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;

import com.pranav.common.util.FileUtil;
import com.pranav.java.ide.MainActivity;
import com.pranav.java.ide.R;
import com.pranav.java.ide.ui.treeview.TreeNode;
import com.pranav.java.ide.ui.treeview.TreeView;
import com.pranav.java.ide.ui.treeview.TreeUtil;
import com.pranav.java.ide.ui.treeview.binder.TreeFileNodeViewBinder;
import com.pranav.java.ide.ui.treeview.binder.TreeFileNodeViewFactory;
import com.pranav.java.ide.ui.treeview.file.TreeFile;
import com.pranav.java.ide.ui.treeview.model.TreeFolder;
import com.pranav.java.ide.ui.utils.UiUtilsKt;
import com.pranav.project.mode.JavaProject;
import com.pranav.project.mode.JavaTemplate;

import java.io.File;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TreeViewDrawer extends Fragment {

    private TreeView<TreeFile> treeView;

    private AlertDialog createNewFileDialog;
    private AlertDialog createNewDirectoryDialog;
    private AlertDialog confirmDeleteDialog;
    private AlertDialog renameFileDialog;

    private MainActivity activity;

    public TreeViewDrawer() {
        super(R.layout.drawer_treeview);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewCompat.requestApplyInsets(view);
        UiUtilsKt.addSystemWindowInsetToPadding(view, false, true, false, true);

        activity = ((MainActivity) getContext());

        buildCreateFileDialog();
        buildCreateDirectoryDialog();
        buildConfirmDeleteDialog();
        buildRenameFileDialog();

        /* Initialize TreeView */
        treeView = new TreeView<TreeFile>(requireContext(), TreeNode.root(Collections.emptyList()));

        /* Add TreeView into HorizontalScrollView */
        HorizontalScrollView horizontalScrollView = view.findViewById(R.id.horizontalScrollView);
        horizontalScrollView.addView(
                treeView.getView(),
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TreeNode<TreeFile> currentNode = treeView.getRoot();
        if(currentNode != null) {
            partialRefresh();
        } else {
            TreeNode<TreeFile> node = TreeNode.root(TreeUtil.getNodes(new File(activity.getProject().getProjectDirPath())));
            treeView.refreshTreeView(node);
        }

        SwipeRefreshLayout refreshLayout = view.findViewById(R.id.refreshLayout);
        refreshLayout.setOnRefreshListener(() -> {
            partialRefresh();
            refreshLayout.setRefreshing(false);
        });

        /* Finally - set adapter for TreeView and assign a listener to it */
        treeView.setAdapter(
                new TreeFileNodeViewFactory(
                        new TreeFileNodeViewBinder.TreeFileNodeListener() {
                            @Override
                            public void onNodeToggled(
                                    @Nullable TreeNode<TreeFile> treeNode, boolean expanded) {
                                if(treeNode.isLeaf()) {
                                    if(treeNode.getValue().getFile().isFile()) {
                                        try {
                                            activity.loadFileToEditor(
                                                treeNode.getValue().getFile().getPath());
                                            if (activity.drawer.isDrawerOpen(GravityCompat.START)) {
                                                activity.drawer.close();
                                            }
                                        } catch (Exception e) {
                                            activity.dialog("Cannot read file", e.getMessage(), true);
                                        }
                                    }
                                }
                            }

                            @Override
                            public boolean onNodeLongClicked(
                                    @Nullable View view,
                                    @Nullable TreeNode<TreeFile> treeNode,
                                    boolean expanded) {
                                showPopup(view, treeNode);
                                return true;
                            }
                        }));
    }

    @Override
    public void onResume() {
        super.onResume();
        partialRefresh();
    }

    private void partialRefresh() {
        if(!treeView.getAllNodes().isEmpty()) {
            TreeNode<TreeFile> node = treeView.getAllNodes().get(0);
            TreeUtil.updateNode(node);
            treeView.refreshTreeView();
        }
    }

    private void showPopup(View v, TreeNode<TreeFile> node) {
        var popup = new PopupMenu(activity, v);
        var inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.treeview_menu, popup.getMenu());
        popup.show();

        if (node.getContent().getFile().getName().equals(activity.getProject().getProjectName()) && node.getLevel() == 0) {
            /* Disable Option to delete the root folder  */
            popup.getMenu().getItem(2).setVisible(false);
        }

        if (node.getContent().getFile().isFile()) {
            /* We cannot create a new class or directory inside a file so we should disable these options */
            popup.getMenu().getItem(0).setVisible(false);
            popup.getMenu().getItem(1).setVisible(false);
            popup.getMenu().getItem(2).setVisible(false);
        }

        popup.setOnMenuItemClickListener(item -> {
            var id = item.getItemId();
            if (id == R.id.create_kotlin_class_menu_bttn) {
                showCreateNewKotlinFileDialog(node);
            } else if (id == R.id.create_java_class_menu_bttn) {
                showCreateNewJavaFileDialog(node);
            } else if (id == R.id.create_directory_bttn) {
                showCreateNewDirectoryDialog(node);
            } else if (id == R.id.delete_menu_bttn) {
                showConfirmDeleteDialog(node);
            } else if (id == R.id.rename_menu_bttn) {
                showRenameFileDialog(node);
            }
            return false;
        });
    }

    private void buildCreateFileDialog() {
        var builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(getString(R.string.create_new_file));
        builder.setView(R.layout.treeview_create_new_file_dialog);
        builder.setPositiveButton(getString(R.string.create), null);
        builder.setNegativeButton(android.R.string.cancel, null);
        createNewFileDialog = builder.create();
    }

    private void buildCreateDirectoryDialog() {
        var builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(getString(R.string.create_new_directory));
        builder.setView(R.layout.treeview_create_new_folder_dialog);
        builder.setPositiveButton(getString(R.string.create), null);
        builder.setNegativeButton(android.R.string.cancel, null);
        createNewDirectoryDialog = builder.create();
    }

    private void buildConfirmDeleteDialog() {
        var builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(getString(R.string.delete));
        builder.setMessage(getString(R.string.delete_file));
        builder.setPositiveButton(getString(R.string.delete), null);
        builder.setNegativeButton(android.R.string.cancel, null);
        confirmDeleteDialog = builder.create();
    }

    private void buildRenameFileDialog() {
        var builder = new MaterialAlertDialogBuilder(getContext());
        builder.setTitle(getString(R.string.rename));
        builder.setView(R.layout.treeview_rename_dialog);
        builder.setPositiveButton(getString(R.string.create), null);
        builder.setNegativeButton(android.R.string.cancel, null);
        renameFileDialog = builder.create();
    }

    private void showRenameFileDialog(TreeNode<TreeFile> node) {
        if (!renameFileDialog.isShowing()) {
            renameFileDialog.show();

            EditText fileName = renameFileDialog.findViewById(android.R.id.text1);
            Button createBttn = renameFileDialog.findViewById(android.R.id.button1);
            fileName.setText(node.getContent().getFile().getPath());

            createBttn.setOnClickListener(
                    v -> {
                        var fileNameString = fileName.getText().toString().replace("..", "");

                        if (!fileNameString.isEmpty()) {
                            try {
                                var path =
                                        Paths.get(
                                                node.getContent().getFile().getPath());
                                Files.move(path, path.resolveSibling(fileNameString));
                                partialRefresh();

                                renameFileDialog.dismiss();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
        }
    }

    private void showCreateNewJavaFileDialog(TreeNode<TreeFile> node) {
        if (!createNewFileDialog.isShowing()) {
            createNewFileDialog.show();

            ((TextInputLayout) createNewFileDialog.findViewById(R.id.inputLayout)).setSuffixText(getString(R.string.java_file_suffix));

            EditText fileName = createNewFileDialog.findViewById(android.R.id.text1);
            Button createBttn = createNewFileDialog.findViewById(android.R.id.button1);

            createBttn.setOnClickListener(
                    v -> {
                        var fileNameString = fileName.getText().toString().replace("..", "");

                        if (!fileNameString.isEmpty()) {
                            try {
                                var filePth =
                                        new File(
                                                node.getContent().getFile().getPath()
                                                        + "/"
                                                        + fileNameString
                                                        + ".java");

                                FileUtil.writeFileFromString(
                                        node.getContent().getFile().getPath() + 
                                        "/" + 
                                        fileNameString +
                                        ".java", JavaTemplate.getClassTemplate(node.getContent().getFile().getName(), fileNameString, false));

                                var newDir =
                                        new TreeNode<TreeFile>(
                                                new TreeFile(filePth),
                                                node.getLevel() + 1); // Get Level of parent so it will have
                                // correct margin and disable some
                                // popup functions if needed
                                node.addChild(newDir);
                                treeView.refreshTreeView();
                                fileName.setText("");
                                createNewFileDialog.dismiss();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
        }
    }

    private void showCreateNewKotlinFileDialog(TreeNode<TreeFile> node) {
        if (!createNewFileDialog.isShowing()) {
            createNewFileDialog.show();

            ((TextInputLayout) createNewFileDialog.findViewById(R.id.inputLayout)).setSuffixText(getString(R.string.kotlin_file_suffix));

            EditText fileName = createNewFileDialog.findViewById(android.R.id.text1);
            Button createBttn = createNewFileDialog.findViewById(android.R.id.button1);

            createBttn.setOnClickListener(
                    v -> {
                        var fileNameString = fileName.getText().toString().replace("..", "");

                        if (!fileNameString.isEmpty()) {
                            try {
                                var filePth =
                                        new File(
                                                node.getContent().getFile().getPath()
                                                        + "/"
                                                        + fileNameString
                                                        + ".kt");

                                FileUtil.writeFileFromString(
                                        node.getContent().getFile().getPath() + 
                                        "/" + 
                                        fileNameString +
                                        ".kt", JavaTemplate.getKotlinClassTemplate(node.getContent().getFile().getName(), fileNameString, false));

                                var newDir =
                                        new TreeNode<TreeFile>(
                                                new TreeFile(filePth),
                                                node.getLevel() + 1); // Get Level of parent so it will have
                                // correct margin and disable some
                                // popup functions if needed
                                node.addChild(newDir);
                                treeView.refreshTreeView();
                                fileName.setText("");
                                createNewFileDialog.dismiss();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
        }
    }

    private void showCreateNewDirectoryDialog(TreeNode<TreeFile> node) {
        if (!createNewDirectoryDialog.isShowing()) {
            createNewDirectoryDialog.show();

            EditText fileName = createNewDirectoryDialog.findViewById(android.R.id.text1);
            Button createBttn = createNewDirectoryDialog.findViewById(android.R.id.button1);

            createBttn.setOnClickListener(
                    v -> {
                        var fileNameString = fileName.getText().toString().replace("..", "");

                        if (fileNameString != null
                                && !fileNameString.isEmpty()
                                && !fileNameString.contains(".")) {
                            var filePath =
                                    node.getContent().getFile().getPath() + "/" + fileNameString;

                            FileUtil.createDirectory(filePath);
                            var dirPth = new File(filePath);
                            var newDir =
                                    new TreeNode<TreeFile>(
                                            new TreeFolder(dirPth), node.getLevel() + 1);
                            node.addChild(newDir);
                            treeView.refreshTreeView();
                            fileName.setText("");
                            createNewDirectoryDialog.dismiss();
                        } else {
                            if (fileNameString.contains(".")) {
                                fileName.setError("Illegal Char!");
                            }
                            if (fileNameString.isEmpty()) {
                                fileName.setError("Name cannot be empty");
                            }
                        }
                    });
        }
    }

    private void showConfirmDeleteDialog(TreeNode<TreeFile> node) {
        if (!confirmDeleteDialog.isShowing()) {
            confirmDeleteDialog.show();

            TextView areUsure_txt = confirmDeleteDialog.findViewById(android.R.id.message);
            Button confirmBttn = confirmDeleteDialog.findViewById(android.R.id.button1);
            Button cancelBttn = confirmDeleteDialog.findViewById(android.R.id.button2);

            areUsure_txt.setText(
                    getString(R.string.delete_file, node.getContent().getFile().getName()));

            confirmBttn.setOnClickListener(
                    v -> {
                        FileUtil.deleteFile(node.getContent().getFile().getPath());
                        node.getParent().removeChild(node);
                        treeView.refreshTreeView();
                        confirmDeleteDialog.dismiss();
                    });

            cancelBttn.setOnClickListener(v -> confirmDeleteDialog.dismiss());
        }
    }

}
