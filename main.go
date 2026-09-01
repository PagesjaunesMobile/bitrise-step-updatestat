package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"io/fs"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"time"
)

const (
	gitlabGraphURL = "https://gitlab.solocal.com/api/graphql"
	gitlabAPIURL   = "https://gitlab.solocal.com/api/v4/projects"
	targetBranch   = "develop"
)

var httpClient = &http.Client{Timeout: 60 * time.Second}

// --- shell helpers -----------------------------------------------------------

func runGit(args ...string) error {
	fmt.Println("git " + strings.Join(args, " "))
	cmd := exec.Command("git", args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}

func warnIfErr(err error, context string) {
	if err != nil {
		fmt.Fprintf(os.Stderr, "[WARN] %s: %v\n", context, err)
	}
}

func fail(format string, args ...interface{}) {
	fmt.Fprintf(os.Stderr, "[ERROR] "+format+"\n", args...)
	os.Exit(1)
}

// --- stats files -------------------------------------------------------------

func copyStatsFiles(origin, dest string) {
	if _, err := os.Stat(origin); err != nil {
		fmt.Fprintf(os.Stderr, "[WARN] stats dir %s not found, nothing to copy\n", origin)
		return
	}
	err := filepath.WalkDir(origin, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() || filepath.Ext(path) != ".json" {
			return nil
		}
		fmt.Printf("copy %s -> %s\n", path, dest)
		data, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		if err := os.MkdirAll(filepath.Dir(dest), 0o755); err != nil {
			return err
		}
		return os.WriteFile(dest, data, 0o644)
	})
	warnIfErr(err, "copying stats files")
}

func getVersionPDM(doc string) (string, error) {
	re := regexp.MustCompile(`"version":.?"([^"]+)",?`)
	m := re.FindStringSubmatch(doc)
	if m == nil {
		return "", fmt.Errorf("no \"version\" field found in stats file")
	}
	fmt.Println("Version " + m[1])
	return m[1], nil
}

// --- GitLab API --------------------------------------------------------------

func gitlabRequest(method, url string, body []byte, token string) ([]byte, error) {
	var reader io.Reader
	if body != nil {
		reader = bytes.NewReader(body)
	}
	req, err := http.NewRequest(method, url, reader)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return respBody, fmt.Errorf("%s %s: HTTP %d: %s", method, url, resp.StatusCode, respBody)
	}
	return respBody, nil
}

func getRepoID(path, token string) (string, error) {
	query := map[string]string{
		"query": fmt.Sprintf(`query { project(fullPath: %q) { id } }`, path),
	}
	body, _ := json.Marshal(query)
	fmt.Printf("ReqRepo %s\n", body)

	respBody, err := gitlabRequest(http.MethodPost, gitlabGraphURL, body, token)
	if err != nil {
		return "", err
	}
	var resp struct {
		Data struct {
			Project struct {
				ID string `json:"id"`
			} `json:"project"`
		} `json:"data"`
	}
	if err := json.Unmarshal(respBody, &resp); err != nil {
		return "", err
	}
	fmt.Printf("Project %+v\n", resp.Data.Project)
	// the GraphQL id looks like "gid://gitlab/Project/123": keep the numeric part
	re := regexp.MustCompile(`/(\d+)$`)
	m := re.FindStringSubmatch(resp.Data.Project.ID)
	if m == nil {
		return "", fmt.Errorf("cannot extract numeric project id from %q", resp.Data.Project.ID)
	}
	return m[1], nil
}

func removeApprovalRules(repoID, mrIID, token string) {
	rulesURL := fmt.Sprintf("%s/%s/merge_requests/%s/approval_rules", gitlabAPIURL, repoID, mrIID)
	respBody, err := gitlabRequest(http.MethodGet, rulesURL, nil, token)
	if err != nil {
		warnIfErr(err, "fetching approval rules")
		return
	}
	var rules []struct {
		ID json.Number `json:"id"`
	}
	if err := json.Unmarshal(respBody, &rules); err != nil {
		warnIfErr(err, "parsing approval rules")
		return
	}
	fmt.Printf("RULES %s\n", respBody)
	for _, rule := range rules {
		_, err := gitlabRequest(http.MethodDelete, rulesURL+"/"+rule.ID.String(), nil, token)
		warnIfErr(err, "deleting approval rule "+rule.ID.String())
	}
}

func createMergeRequest(repoPath, title, featBranch, token string) error {
	repoID, err := getRepoID(repoPath, token)
	if err != nil {
		return fmt.Errorf("getting repository id: %w", err)
	}
	fmt.Println("Repo ID " + repoID)

	mrRequest := map[string]interface{}{
		"target_branch":          targetBranch,
		"source_branch":          featBranch,
		"id":                     repoID,
		"title":                  title,
		"approvals_before_merge": 0,
		"remove_source_branch":   true,
	}
	body, _ := json.Marshal(mrRequest)
	mrURL := fmt.Sprintf("%s/%s/merge_requests", gitlabAPIURL, repoID)
	respBody, err := gitlabRequest(http.MethodPost, mrURL, body, token)
	if err != nil {
		return fmt.Errorf("creating merge request: %w", err)
	}
	var mr struct {
		ID        json.Number `json:"id"`
		IID       json.Number `json:"iid"`
		ProjectID json.Number `json:"project_id"`
		Title     string      `json:"title"`
	}
	if err := json.Unmarshal(respBody, &mr); err != nil {
		return fmt.Errorf("parsing merge request response: %w", err)
	}
	fmt.Printf("body %+v\n", mr)

	notesURL := fmt.Sprintf("%s/%s/merge_requests/%s/notes", gitlabAPIURL, repoID, mr.IID.String())
	noteResp, err := gitlabRequest(http.MethodPost, notesURL, []byte(`{"body":"code review OK"}`), token)
	if err != nil {
		warnIfErr(err, "posting note")
	} else {
		fmt.Printf("Notes %s\n", noteResp)
	}

	removeApprovalRules(repoID, mr.IID.String(), token)
	return nil
}

// --- main --------------------------------------------------------------------

func main() {
	sourceDir := os.Getenv("BITRISE_SOURCE_DIR")
	if sourceDir == "" {
		sourceDir = "."
	}
	if err := os.Chdir(sourceDir); err != nil {
		fail("cannot change directory to %s: %v", sourceDir, err)
	}

	origin := filepath.Join(sourceDir, "build", "stats")
	destStat := filepath.Join(sourceDir, "stat", "src", "main", "res", "raw", "at.json")
	copyStatsFiles(origin, destStat)

	doc, err := os.ReadFile(destStat)
	if err != nil {
		fail("cannot read stats file %s: %v", destStat, err)
	}
	version, err := getVersionPDM(string(doc))
	if err != nil {
		fail("%v", err)
	}

	featBranch := "feat/updateStat_" + version
	title := fmt.Sprintf("feat(stat): update PDM %s", version)
	fmt.Println(title)

	warnIfErr(runGit("checkout", "-b", featBranch), "git checkout")
	warnIfErr(runGit("add", "."), "git add")
	warnIfErr(runGit("commit", "-am", title), "git commit")
	warnIfErr(runGit("push", "origin", featBranch), "git push")

	repoURL := os.Getenv("GIT_REPOSITORY_URL")
	repoPattern := regexp.MustCompile(`:(.*)\.git`)
	m := repoPattern.FindStringSubmatch(repoURL)
	token := os.Getenv("GITLAB_API_TOKEN")
	if m == nil || token == "" {
		fmt.Fprintf(os.Stderr, "[WARN] no GitLab repository path in GIT_REPOSITORY_URL (%q) or GITLAB_API_TOKEN not set, skipping merge request creation\n", repoURL)
		return
	}
	repo := m[1]
	fmt.Println("Repo " + repo)

	if err := createMergeRequest(repo, "update PDM "+version, featBranch, token); err != nil {
		fail("%v", err)
	}
}
