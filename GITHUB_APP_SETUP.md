# GITHUB_APP_SETUP.md

## Setting Up a GitHub App Token for Dependency Version Update Workflow

This document provides comprehensive instructions for implementing a GitHub App Token in your Dependency Version Update
workflow.

### 1. Create the GitHub App

1. **Go to GitHub Settings**: Navigate to your GitHub account settings.
2. **Select Developer settings**: Find "Developer settings" on the left sidebar.
3. **Create a New GitHub App**:
    - Click on "GitHub Apps".
    - Click on the "New GitHub App" button.

4. **Configure Your App**:
    - Fill out the required fields:
        - **GitHub App name**: Give your app a name.
        - **Homepage URL**: Provide a URL for your app's homepage.
        - **User authorization callback URL**: Specify a callback URL where GitHub redirects to after user
          authorization.
    - **App permissions**: Set appropriate permissions for your app. For dependency updates, you will need:
        - `Read` access to `metadata` and `contents`.
        - `Read & Write` access to `pull requests`.
    - **Webhooks**: Configure any necessary webhooks if your app needs to listen to GitHub events.

5. **Create App**: Click on the "Create GitHub App" button to save your configuration.

### 2. Store Credentials

After creating your GitHub App, you will receive the following:

- **Client ID**: Used for OAuth2 authentication.
- **Client Secret**: Used for authenticating your app.
- **Private Key**: Generate a private key to authenticate your app programmatically. Download it and store it securely.

### 3. Install the App

1. **Install the App on Your Repository**:
    - Go to your GitHub App settings and find your new app.
    - Click on "Install App".
    - Choose the repositories where your app should have access. Select your target repository for the dependency
      updates.

### 4. Update Workflow with Code Examples

You will need to incorporate the GitHub App Token into your workflow using a GitHub Actions workflow file. Here’s an
example of how to do that:

#### Example Workflow YAML

```yaml
name: Dependency Version Update Workflow

on:
  schedule:
    - cron: '0 0 * * 1'  # Run every Monday at midnight

jobs:
  update_dependencies:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v2
      - name: Set up Node.js
        uses: actions/setup-node@v2
        with:
          node-version: '14'
      - name: Authenticate with GitHub App Token
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_APP_TOKEN }}
        run: |
          echo "Authenticating..."
          curl -X POST -H 'Authorization: Bearer $GITHUB_TOKEN' 'https://api.github.com/repos/OWNER/REPO/pulls'

      # Add more steps here to update dependencies and create PRs
```
