export class FilenameFormatter {
  private static ILLEGAL_CHARS = /[\\/:*?"<>|]/g;

  static sanitize(name: string): string {
    let clean = name
      .replace(this.ILLEGAL_CHARS, '_')
      .replace(/\r/g, '')
      .replace(/\n/g, '')
      .trim();

    if (!clean) clean = 'media_download';
    if (clean.length > 180) {
      clean = clean.substring(0, 180).trim();
    }
    return clean;
  }

  static format(
    template: string,
    title: string,
    uploader: string = 'Unknown',
    id: string = 'media',
    ext: string = 'mp4',
    uploadDate: string = '',
    resolution: string = ''
  ): string {
    let result = template?.trim() ? template : '%(title)s.%(ext)s';

    const cleanTitle = this.sanitize(title);
    const cleanUploader = this.sanitize(uploader);
    const cleanId = this.sanitize(id);
    const cleanExt = this.sanitize(ext.replace(/^\./, ''));
    const dateStr = uploadDate ? this.sanitize(uploadDate) : new Date().toISOString().slice(0, 10).replace(/-/g, '');

    result = result
      .replace(/%\(title\)s/g, cleanTitle)
      .replace(/%\(uploader\)s/g, cleanUploader)
      .replace(/%\(channel\)s/g, cleanUploader)
      .replace(/%\(id\)s/g, cleanId)
      .replace(/%\(ext\)s/g, cleanExt)
      .replace(/%\(upload_date\)s/g, dateStr)
      .replace(/%\(resolution\)s/g, this.sanitize(resolution));

    const parts = result.split('/');
    const sanitizedParts = parts.map((p) => this.sanitize(p)).filter((p) => p.length > 0);

    const finalName = sanitizedParts.join('/');
    if (finalName.toLowerCase().endsWith(`.${cleanExt.toLowerCase()}`)) {
      return finalName;
    } else {
      return `${finalName}.${cleanExt}`;
    }
  }
}
